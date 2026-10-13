/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <android/hardware_buffer.h>
#include <vndk/hardware_buffer.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <linux/memfd.h>
#include <unistd.h>
#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>

#include <cstdio>
#include <cstring>
#include <set>
#include <string>
#include <vector>

namespace {

struct Fmt {
    VkFormat format;
    const char* name;
};

const Fmt kFormats[] = {
        {VK_FORMAT_R8G8B8A8_UNORM, "R8G8B8A8_UNORM"},
        {VK_FORMAT_B8G8R8A8_UNORM, "B8G8R8A8_UNORM"},
        {VK_FORMAT_R8G8B8A8_SRGB, "R8G8B8A8_SRGB"},
        {VK_FORMAT_B8G8R8A8_SRGB, "B8G8R8A8_SRGB"},
        {VK_FORMAT_R5G6B5_UNORM_PACK16, "R5G6B5_UNORM"},
        {VK_FORMAT_A2B10G10R10_UNORM_PACK32, "A2B10G10R10_UNORM"},
        {VK_FORMAT_A2R10G10B10_UNORM_PACK32, "A2R10G10B10_UNORM"},
        {VK_FORMAT_R16G16B16A16_SFLOAT, "R16G16B16A16_SFLOAT"},
};

struct Handle {
    VkExternalMemoryHandleTypeFlagBits type;
    const char* name;
};

const Handle kHandles[] = {
        {VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID, "AHB"},
        {VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, "DMABUF"},
        {VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT, "OPAQUE_FD"},
};

constexpr VkImageUsageFlags kUsage =
        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT |
        VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
constexpr VkImageCreateFlags kMutable =
        VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT | VK_IMAGE_CREATE_EXTENDED_USAGE_BIT;

const char* resultName(VkResult r) {
    switch (r) {
        case VK_SUCCESS: return "ok";
        case VK_ERROR_FORMAT_NOT_SUPPORTED: return "NOT_SUPPORTED";
        case VK_ERROR_OUT_OF_HOST_MEMORY: return "OOM_HOST";
        case VK_ERROR_OUT_OF_DEVICE_MEMORY: return "OOM_DEVICE";
        case VK_ERROR_INVALID_EXTERNAL_HANDLE: return "INVALID_HANDLE";
        case VK_ERROR_INITIALIZATION_FAILED: return "INIT_FAILED";
        default: return "error";
    }
}

std::string featureString(VkExternalMemoryFeatureFlags f) {
    std::string s;
    s += (f & VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT) ? "E" : "-";
    s += (f & VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT) ? "I" : "-";
    s += (f & VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT) ? "D" : "-";
    return s;
}

std::string queryCell(VkPhysicalDevice pd, VkFormat format, VkImageTiling tiling,
                      VkImageCreateFlags flags, VkExternalMemoryHandleTypeFlagBits handle,
                      uint64_t modifier = 0) {
    VkPhysicalDeviceImageDrmFormatModifierInfoEXT modInfo = {
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_DRM_FORMAT_MODIFIER_INFO_EXT, nullptr, modifier,
            VK_SHARING_MODE_EXCLUSIVE, 0, nullptr};
    VkPhysicalDeviceExternalImageFormatInfo extInfo = {
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_IMAGE_FORMAT_INFO,
            tiling == VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT ? &modInfo : nullptr, handle};
    VkPhysicalDeviceImageFormatInfo2 info = {
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_FORMAT_INFO_2,
            &extInfo,
            format,
            VK_IMAGE_TYPE_2D,
            tiling,
            kUsage,
            flags};
    VkExternalImageFormatProperties extProps = {
            VK_STRUCTURE_TYPE_EXTERNAL_IMAGE_FORMAT_PROPERTIES, nullptr, {}};
    VkImageFormatProperties2 props = {VK_STRUCTURE_TYPE_IMAGE_FORMAT_PROPERTIES_2, &extProps, {}};
    VkResult r = vkGetPhysicalDeviceImageFormatProperties2(pd, &info, &props);
    if (r != VK_SUCCESS) return resultName(r);
    return featureString(extProps.externalMemoryProperties.externalMemoryFeatures);
}

void printQueryTable(VkPhysicalDevice pd) {
    printf("\n== vkGetPhysicalDeviceImageFormatProperties2, usage=COLOR|SAMPLED|XFER_SRC|XFER_DST\n");
    printf("   cell = E(xportable) I(mportable) D(edicated only), or the error\n\n");
    for (VkImageCreateFlags flags : {VkImageCreateFlags(0), kMutable}) {
        for (VkImageTiling tiling : {VK_IMAGE_TILING_OPTIMAL, VK_IMAGE_TILING_LINEAR}) {
            printf("-- tiling=%s flags=%s\n", tiling == VK_IMAGE_TILING_OPTIMAL ? "OPTIMAL" : "LINEAR",
                   flags ? "MUTABLE|EXTENDED_USAGE" : "0");
            printf("%-22s", "format");
            for (const auto& h : kHandles) printf(" %-14s", h.name);
            printf("\n");
            for (const auto& f : kFormats) {
                printf("%-22s", f.name);
                for (const auto& h : kHandles) {
                    printf(" %-14s", queryCell(pd, f.format, tiling, flags, h.type).c_str());
                }
                printf("\n");
            }
            printf("\n");
        }
    }
}

std::vector<std::vector<uint64_t>> printModifiers(VkPhysicalDevice pd, bool hasModifierExt) {
    std::vector<std::vector<uint64_t>> all(std::size(kFormats));
    printf("== DRM format modifiers%s\n", hasModifierExt ? "" : " (extension not supported)");
    if (!hasModifierExt) return all;
    for (size_t fi = 0; fi < std::size(kFormats); ++fi) {
        const auto& f = kFormats[fi];
        VkDrmFormatModifierPropertiesListEXT list = {
                VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_EXT, nullptr, 0, nullptr};
        VkFormatProperties2 fp = {VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2, &list, {}};
        vkGetPhysicalDeviceFormatProperties2(pd, f.format, &fp);
        std::vector<VkDrmFormatModifierPropertiesEXT> mods(list.drmFormatModifierCount);
        list.pDrmFormatModifierProperties = mods.data();
        vkGetPhysicalDeviceFormatProperties2(pd, f.format, &fp);
        printf("%-22s", f.name);
        for (const auto& m : mods) {
            printf(" 0x%016llx(planes=%u)", (unsigned long long)m.drmFormatModifier,
                   m.drmFormatModifierPlaneCount);
            all[fi].push_back(m.drmFormatModifier);
        }
        printf("%s\n", mods.empty() ? " none" : "");
    }
    printf("\n");
    return all;
}

void printModifierQueryTable(VkPhysicalDevice pd, const std::vector<std::vector<uint64_t>>& mods) {
    printf("== modifier tiling, flags=MUTABLE|EXTENDED_USAGE\n");
    printf("%-22s %-20s", "format", "modifier");
    for (const auto& h : kHandles) printf(" %-14s", h.name);
    printf("\n");
    for (size_t fi = 0; fi < std::size(kFormats); ++fi) {
        for (uint64_t m : mods[fi]) {
            printf("%-22s 0x%016llx  ", kFormats[fi].name, (unsigned long long)m);
            for (const auto& h : kHandles) {
                printf(" %-14s", queryCell(pd, kFormats[fi].format,
                                           VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT, kMutable,
                                           h.type, m).c_str());
            }
            printf("\n");
        }
    }
    printf("\n");
}

uint32_t pickMemoryType(const VkPhysicalDeviceMemoryProperties& mp, uint32_t bits) {
    for (uint32_t i = 0; i < mp.memoryTypeCount; ++i) {
        if ((bits & (1u << i)) &&
            (mp.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
            return i;
        }
    }
    for (uint32_t i = 0; i < mp.memoryTypeCount; ++i) {
        if (bits & (1u << i)) return i;
    }
    return UINT32_MAX;
}

std::string exportTest(VkDevice dev, const VkPhysicalDeviceMemoryProperties& mp,
                       VkFormat format, VkExternalMemoryHandleTypeFlagBits handle,
                       const uint64_t* modifier = nullptr) {
    VkImageDrmFormatModifierListCreateInfoEXT modList = {
            VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_LIST_CREATE_INFO_EXT, nullptr, 1, modifier};
    VkExternalMemoryImageCreateInfo extCi = {VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
                                             modifier ? &modList : nullptr,
                                             (VkExternalMemoryHandleTypeFlags)handle};
    VkImageCreateInfo ci = {};
    ci.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ci.pNext = &extCi;
    ci.flags = kMutable;
    ci.imageType = VK_IMAGE_TYPE_2D;
    ci.format = format;
    ci.extent = {1280, 720, 1};
    ci.mipLevels = 1;
    ci.arrayLayers = 1;
    ci.samples = VK_SAMPLE_COUNT_1_BIT;
    ci.tiling = modifier ? VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT : VK_IMAGE_TILING_OPTIMAL;
    ci.usage = kUsage;
    ci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ci.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

    VkImage image = VK_NULL_HANDLE;
    VkResult r = vkCreateImage(dev, &ci, nullptr, &image);
    if (r != VK_SUCCESS) return std::string("createImage:") + resultName(r);

    VkMemoryRequirements req;
    vkGetImageMemoryRequirements(dev, image, &req);

    VkMemoryDedicatedAllocateInfo ded = {VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO, nullptr,
                                         image, VK_NULL_HANDLE};
    VkExportMemoryAllocateInfo exp = {VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO, &ded,
                                      (VkExternalMemoryHandleTypeFlags)handle};
    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, &exp, req.size,
                               pickMemoryType(mp, req.memoryTypeBits)};
    VkDeviceMemory mem = VK_NULL_HANDLE;
    r = vkAllocateMemory(dev, &ai, nullptr, &mem);
    if (r != VK_SUCCESS) {
        vkDestroyImage(dev, image, nullptr);
        return std::string("alloc:") + resultName(r);
    }

    std::string out;
    if (handle == VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID) {
        auto getAhb = (PFN_vkGetMemoryAndroidHardwareBufferANDROID)vkGetDeviceProcAddr(
                dev, "vkGetMemoryAndroidHardwareBufferANDROID");
        VkMemoryGetAndroidHardwareBufferInfoANDROID gi = {
                VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID, nullptr, mem};
        AHardwareBuffer* ahb = nullptr;
        r = getAhb ? getAhb(dev, &gi, &ahb) : VK_ERROR_EXTENSION_NOT_PRESENT;
        if (r == VK_SUCCESS && ahb) {
            AHardwareBuffer_Desc d = {};
            AHardwareBuffer_describe(ahb, &d);
            char buf[96];
            snprintf(buf, sizeof(buf), "ok ahbfmt=%u stride=%u", d.format, d.stride);
            out = buf;
            AHardwareBuffer_release(ahb);
        } else {
            out = std::string("export:") + resultName(r);
        }
    } else {
        auto getFd = (PFN_vkGetMemoryFdKHR)vkGetDeviceProcAddr(dev, "vkGetMemoryFdKHR");
        VkMemoryGetFdInfoKHR gi = {VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR, nullptr, mem, handle};
        int fd = -1;
        r = getFd ? getFd(dev, &gi, &fd) : VK_ERROR_EXTENSION_NOT_PRESENT;
        if (r == VK_SUCCESS && fd >= 0) {
            off_t size = lseek(fd, 0, SEEK_END);
            void* p = mmap(nullptr, req.size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
            char buf[96];
            snprintf(buf, sizeof(buf), "ok fd size=%lld mmap=%s", (long long)size,
                     p == MAP_FAILED ? "FAIL" : "ok");
            out = buf;
            if (p != MAP_FAILED) munmap(p, req.size);
            close(fd);
        } else {
            out = std::string("export:") + resultName(r);
        }
    }

    vkFreeMemory(dev, mem, nullptr);
    vkDestroyImage(dev, image, nullptr);
    return out;
}

void grallocImportTest(VkDevice dev, const VkPhysicalDeviceMemoryProperties& mp,
                       uint64_t extraUsage, const char* label) {
    printf("-- gralloc BGRA_8888 (AHB format 5), usage GPU_COLOR|GPU_SAMPLED%s\n", label);
    AHardwareBuffer_Desc desc = {};
    desc.width = 1280;
    desc.height = 720;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_B8G8R8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE |
                 extraUsage;
    AHardwareBuffer* ahb = nullptr;
    if (AHardwareBuffer_allocate(&desc, &ahb) != 0) {
        printf("   gralloc allocate: FAILED\n");
        return;
    }
    AHardwareBuffer_describe(ahb, &desc);
    const native_handle_t* nh = AHardwareBuffer_getNativeHandle(ahb);
    int fd = nh && nh->numFds > 0 ? nh->data[0] : -1;
    printf("   gralloc ok: stride=%u px, fd=%d, numFds=%d numInts=%d\n", desc.stride, fd,
           nh ? nh->numFds : -1, nh ? nh->numInts : -1);

    auto getFdProps = (PFN_vkGetMemoryFdPropertiesKHR)vkGetDeviceProcAddr(
            dev, "vkGetMemoryFdPropertiesKHR");
    VkMemoryFdPropertiesKHR fdProps = {VK_STRUCTURE_TYPE_MEMORY_FD_PROPERTIES_KHR, nullptr, 0};
    VkResult r = getFdProps(dev, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, fd, &fdProps);
    printf("   vkGetMemoryFdPropertiesKHR: %s typeBits=0x%x\n", resultName(r),
           fdProps.memoryTypeBits);

    VkSubresourceLayout layout = {0, 0, (VkDeviceSize)desc.stride * 4, 0, 0};
    VkImageDrmFormatModifierExplicitCreateInfoEXT explicitInfo = {
            VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT, nullptr, 0, 1,
            &layout};
    VkExternalMemoryImageCreateInfo extCi = {VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
                                             &explicitInfo,
                                             VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkImageCreateInfo ci = {};
    ci.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ci.pNext = &extCi;
    ci.flags = kMutable;
    ci.imageType = VK_IMAGE_TYPE_2D;
    ci.format = VK_FORMAT_B8G8R8A8_UNORM;
    ci.extent = {desc.width, desc.height, 1};
    ci.mipLevels = 1;
    ci.arrayLayers = 1;
    ci.samples = VK_SAMPLE_COUNT_1_BIT;
    ci.tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT;
    ci.usage = kUsage;
    ci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ci.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    VkImage image = VK_NULL_HANDLE;
    r = vkCreateImage(dev, &ci, nullptr, &image);
    printf("   vkCreateImage BGRA/LINEAR modifier, dma-buf: %s\n", resultName(r));
    if (r != VK_SUCCESS) {
        AHardwareBuffer_release(ahb);
        return;
    }

    VkMemoryRequirements req;
    vkGetImageMemoryRequirements(dev, image, &req);
    VkMemoryDedicatedAllocateInfo ded = {VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO, nullptr,
                                         image, VK_NULL_HANDLE};
    VkImportMemoryFdInfoKHR importInfo = {VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR, &ded,
                                          VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, dup(fd)};
    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, &importInfo, req.size,
                               pickMemoryType(mp, req.memoryTypeBits &
                                                          (fdProps.memoryTypeBits
                                                                   ? fdProps.memoryTypeBits
                                                                   : ~0u))};
    VkDeviceMemory mem = VK_NULL_HANDLE;
    r = vkAllocateMemory(dev, &ai, nullptr, &mem);
    printf("   import vkAllocateMemory (size %llu): %s\n", (unsigned long long)req.size,
           resultName(r));
    if (r == VK_SUCCESS) {
        r = vkBindImageMemory(dev, image, mem, 0);
        printf("   vkBindImageMemory: %s\n", resultName(r));
    }

    if (r == VK_SUCCESS) {
        VkQueue queue;
        vkGetDeviceQueue(dev, 0, 0, &queue);
        VkCommandPoolCreateInfo pci = {VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, nullptr, 0, 0};
        VkCommandPool pool;
        vkCreateCommandPool(dev, &pci, nullptr, &pool);
        VkCommandBufferAllocateInfo cbai = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, nullptr,
                                            pool, VK_COMMAND_BUFFER_LEVEL_PRIMARY, 1};
        VkCommandBuffer cb;
        vkAllocateCommandBuffers(dev, &cbai, &cb);
        VkCommandBufferBeginInfo bi = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO, nullptr,
                                       VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT, nullptr};
        vkBeginCommandBuffer(cb, &bi);
        VkImageSubresourceRange range = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        VkImageMemoryBarrier toDst = {VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, nullptr, 0,
                                      VK_ACCESS_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_UNDEFINED,
                                      VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                      VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED, image, range};
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                             0, 0, nullptr, 0, nullptr, 1, &toDst);
        VkClearColorValue color = {{0.0f, 0.25f, 0.5f, 1.0f}};
        vkCmdClearColorImage(cb, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &color, 1, &range);
        VkImageMemoryBarrier toForeign = toDst;
        toForeign.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        toForeign.dstAccessMask = 0;
        toForeign.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        toForeign.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        toForeign.srcQueueFamilyIndex = 0;
        toForeign.dstQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT;
        vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                             VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0, nullptr, 1,
                             &toForeign);
        vkEndCommandBuffer(cb);
        VkSubmitInfo si = {VK_STRUCTURE_TYPE_SUBMIT_INFO, nullptr, 0, nullptr, nullptr, 1, &cb,
                           0, nullptr};
        r = vkQueueSubmit(queue, 1, &si, VK_NULL_HANDLE);
        vkQueueWaitIdle(queue);
        printf("   GPU clear to (r=0 g=64 b=128 a=255): %s\n", resultName(r));
        vkDestroyCommandPool(dev, pool, nullptr);

        if (desc.usage & AHARDWAREBUFFER_USAGE_CPU_READ_MASK) {
            void* ptr = nullptr;
            if (AHardwareBuffer_lock(ahb, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, nullptr,
                                     &ptr) == 0 && ptr) {
                const uint8_t* p = static_cast<const uint8_t*>(ptr);
                const uint8_t* last = p + ((size_t)(desc.height - 1) * desc.stride +
                                           (desc.width - 1)) * 4;
                printf("   CPU read via gralloc: first px bytes %u %u %u %u, last px %u %u %u %u"
                       " (expect 128 64 0 255 for B,G,R,A)\n",
                       p[0], p[1], p[2], p[3], last[0], last[1], last[2], last[3]);
                AHardwareBuffer_unlock(ahb, nullptr);
            } else {
                printf("   CPU lock: FAILED\n");
            }
        }
    }

    if (mem) vkFreeMemory(dev, mem, nullptr);
    vkDestroyImage(dev, image, nullptr);
    AHardwareBuffer_release(ahb);
}

void hostPointerImportTest(VkPhysicalDevice pd, VkDevice dev,
                           const VkPhysicalDeviceMemoryProperties& mp) {
    printf("\n== memfd host pointer import (gfxstream SystemBlob)\n");
    VkPhysicalDeviceExternalMemoryHostPropertiesEXT hostProps = {
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_MEMORY_HOST_PROPERTIES_EXT, nullptr, 0};
    VkPhysicalDeviceProperties2 props2 = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2,
                                          &hostProps, {}};
    vkGetPhysicalDeviceProperties2(pd, &props2);
    printf("   minImportedHostPointerAlignment=%llu\n",
           (unsigned long long)hostProps.minImportedHostPointerAlignment);
    for (uint32_t i = 0; i < mp.memoryTypeCount; ++i) {
        printf("   memory type %u: flags 0x%x heap %u\n", i, mp.memoryTypes[i].propertyFlags,
               mp.memoryTypes[i].heapIndex);
    }

    const size_t size = 4 << 20;
    int fd = (int)syscall(__NR_memfd_create, "vk_probe", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    if (fd < 0 || ftruncate(fd, size) != 0) {
        printf("   memfd: FAILED\n");
        return;
    }
    void* ptr = mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (ptr == MAP_FAILED) {
        printf("   mmap: FAILED\n");
        close(fd);
        return;
    }

    auto getHostPtrProps = (PFN_vkGetMemoryHostPointerPropertiesEXT)vkGetDeviceProcAddr(
            dev, "vkGetMemoryHostPointerPropertiesEXT");
    VkMemoryHostPointerPropertiesEXT ptrProps = {
            VK_STRUCTURE_TYPE_MEMORY_HOST_POINTER_PROPERTIES_EXT, nullptr, 0};
    VkResult r = getHostPtrProps
                         ? getHostPtrProps(dev,
                                           VK_EXTERNAL_MEMORY_HANDLE_TYPE_HOST_ALLOCATION_BIT_EXT,
                                           ptr, &ptrProps)
                         : VK_ERROR_EXTENSION_NOT_PRESENT;
    printf("   vkGetMemoryHostPointerPropertiesEXT: %s typeBits=0x%x\n", resultName(r),
           ptrProps.memoryTypeBits);

    for (uint32_t i = 0; r == VK_SUCCESS && i < mp.memoryTypeCount; ++i) {
        if (!(ptrProps.memoryTypeBits & (1u << i))) continue;
        VkImportMemoryHostPointerInfoEXT importInfo = {
                VK_STRUCTURE_TYPE_IMPORT_MEMORY_HOST_POINTER_INFO_EXT, nullptr,
                VK_EXTERNAL_MEMORY_HANDLE_TYPE_HOST_ALLOCATION_BIT_EXT, ptr};
        VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, &importInfo, size, i};
        VkDeviceMemory mem = VK_NULL_HANDLE;
        VkResult ar = vkAllocateMemory(dev, &ai, nullptr, &mem);
        std::string mapRes = "-";
        if (ar == VK_SUCCESS &&
            (mp.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)) {
            void* mapped = nullptr;
            VkResult mr = vkMapMemory(dev, mem, 0, VK_WHOLE_SIZE, 0, &mapped);
            mapRes = mr == VK_SUCCESS ? (mapped == ptr ? "same-ptr" : "other-ptr") : resultName(mr);
            if (mr == VK_SUCCESS) vkUnmapMemory(dev, mem);
        }

        std::string bufRes = "-";
        if (ar == VK_SUCCESS) {
            VkExternalMemoryBufferCreateInfo extBuf = {
                    VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_BUFFER_CREATE_INFO, nullptr,
                    VK_EXTERNAL_MEMORY_HANDLE_TYPE_HOST_ALLOCATION_BIT_EXT};
            VkBufferCreateInfo bci = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, &extBuf, 0, size,
                                      VK_BUFFER_USAGE_TRANSFER_SRC_BIT |
                                              VK_BUFFER_USAGE_TRANSFER_DST_BIT |
                                              VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
                                              VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                                      VK_SHARING_MODE_EXCLUSIVE, 0, nullptr};
            VkBuffer buf = VK_NULL_HANDLE;
            VkResult br = vkCreateBuffer(dev, &bci, nullptr, &buf);
            if (br == VK_SUCCESS) {
                VkMemoryRequirements req;
                vkGetBufferMemoryRequirements(dev, buf, &req);
                br = (req.memoryTypeBits & (1u << i)) ? vkBindBufferMemory(dev, buf, mem, 0)
                                                     : VK_ERROR_FORMAT_NOT_SUPPORTED;
                vkDestroyBuffer(dev, buf, nullptr);
            }
            bufRes = br == VK_SUCCESS ? "bind ok" : resultName(br);
        }
        printf("   import as type %u (flags 0x%x): alloc %s, map %s, buffer %s\n", i,
               mp.memoryTypes[i].propertyFlags, resultName(ar), mapRes.c_str(), bufRes.c_str());
        if (mem) vkFreeMemory(dev, mem, nullptr);
    }
    munmap(ptr, size);
    close(fd);
}

}  // namespace

int main() {
    VkApplicationInfo app = {VK_STRUCTURE_TYPE_APPLICATION_INFO, nullptr, "vk_ahb_probe", 1,
                             nullptr, 0, VK_API_VERSION_1_1};
    VkInstanceCreateInfo ici = {VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, nullptr, 0, &app,
                                0, nullptr, 0, nullptr};
    VkInstance instance;
    if (vkCreateInstance(&ici, nullptr, &instance) != VK_SUCCESS) {
        fprintf(stderr, "vkCreateInstance failed\n");
        return 1;
    }

    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (!count) {
        fprintf(stderr, "no Vulkan devices\n");
        return 1;
    }
    std::vector<VkPhysicalDevice> pds(count);
    vkEnumeratePhysicalDevices(instance, &count, pds.data());
    VkPhysicalDevice pd = pds[0];

    VkPhysicalDeviceProperties props;
    vkGetPhysicalDeviceProperties(pd, &props);
    printf("device: %s  api %u.%u.%u  driver 0x%x\n", props.deviceName,
           VK_API_VERSION_MAJOR(props.apiVersion), VK_API_VERSION_MINOR(props.apiVersion),
           VK_API_VERSION_PATCH(props.apiVersion), props.driverVersion);

    uint32_t extCount = 0;
    vkEnumerateDeviceExtensionProperties(pd, nullptr, &extCount, nullptr);
    std::vector<VkExtensionProperties> exts(extCount);
    vkEnumerateDeviceExtensionProperties(pd, nullptr, &extCount, exts.data());
    std::set<std::string> available;
    for (const auto& e : exts) available.insert(e.extensionName);

    const char* wanted[] = {
            VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
            VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME,
            VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME,
            VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME,
            VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME,
            VK_EXT_EXTERNAL_MEMORY_HOST_EXTENSION_NAME,
    };
    std::vector<const char*> enabled;
    printf("extensions:");
    for (const char* w : wanted) {
        bool has = available.count(w);
        printf(" %s=%s", w, has ? "yes" : "NO");
        if (has) enabled.push_back(w);
    }
    printf("\n");

    printQueryTable(pd);
    auto mods = printModifiers(pd, available.count(VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME));
    printModifierQueryTable(pd, mods);

    float prio = 1.0f;
    VkDeviceQueueCreateInfo qci = {VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, nullptr, 0, 0, 1,
                                   &prio};
    VkDeviceCreateInfo dci = {VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, nullptr, 0, 1, &qci, 0,
                              nullptr, (uint32_t)enabled.size(), enabled.data(), nullptr};
    VkDevice dev;
    if (vkCreateDevice(pd, &dci, nullptr, &dev) != VK_SUCCESS) {
        fprintf(stderr, "vkCreateDevice failed\n");
        return 1;
    }
    VkPhysicalDeviceMemoryProperties mp;
    vkGetPhysicalDeviceMemoryProperties(pd, &mp);

    printf("== real allocation + export, 1280x720 OPTIMAL MUTABLE, dedicated\n");
    printf("%-22s", "format");
    for (const auto& h : kHandles) printf(" %-34s", h.name);
    printf("\n");
    for (const auto& f : kFormats) {
        printf("%-22s", f.name);
        for (const auto& h : kHandles) {
            bool skip = (h.type == VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT &&
                         !available.count(VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME)) ||
                        (h.type == VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID &&
                         !available.count(
                                 VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME));
            if (skip || queryCell(pd, f.format, VK_IMAGE_TILING_OPTIMAL, kMutable, h.type)[0] != 'E') {
                printf(" %-34s", "(not exportable, skipped)");
                continue;
            }
            printf(" %-34s", exportTest(dev, mp, f.format, h.type).c_str());
            fflush(stdout);
        }
        printf("\n");
    }

    printf("\n== real allocation + export, 1280x720 DRM modifier tiling MUTABLE, dedicated\n");
    for (size_t fi = 0; fi < std::size(kFormats); ++fi) {
        for (uint64_t m : mods[fi]) {
            for (const auto& h : kHandles) {
                if (h.type == VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID) {
                    continue;
                }
                if (queryCell(pd, kFormats[fi].format, VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT,
                              kMutable, h.type, m)[0] != 'E') {
                    continue;
                }
                printf("%-22s 0x%016llx %-10s %s\n", kFormats[fi].name, (unsigned long long)m,
                       h.name, exportTest(dev, mp, kFormats[fi].format, h.type, &m).c_str());
                fflush(stdout);
            }
        }
    }

    hostPointerImportTest(pd, dev, mp);

    printf("\n== gralloc BGRA buffer imported into Vulkan as dma-buf\n");
    grallocImportTest(dev, mp,
                      AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
                      "|CPU_READ|CPU_WRITE");
    grallocImportTest(dev, mp, 0, " (GPU only)");

    vkDestroyDevice(dev, nullptr);
    vkDestroyInstance(instance, nullptr);
    return 0;
}
