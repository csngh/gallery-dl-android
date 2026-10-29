#ifndef VpxShim_h
#define VpxShim_h

#include <stddef.h>
#include <stdint.h>

/// A thin layer over libvpx's decoder. libvpx starts a decoder with a macro that Swift cannot call, so the few calls the
/// app needs are wrapped here as plain functions.
typedef struct VpxDecoder VpxDecoder;

/// One decoded picture. The planes belong to the decoder and stay valid until the next call to `vpxdec_decode`.
typedef struct {
    int width;
    int height;
    const uint8_t *planes[3];
    int strides[3];
    /// 1 when the pixel values use the whole 0-255 range, 0 for the usual 16-235 video range.
    int fullRange;
    /// 1 for BT.601 (standard definition), 2 for BT.709 (HD), 0 when the stream does not say.
    int colorSpace;
} VpxPicture;

VpxDecoder *vpxdec_create(int isVP9, int threads);
void vpxdec_destroy(VpxDecoder *decoder);
/// Feeds one compressed frame. Returns 0 on success.
int vpxdec_decode(VpxDecoder *decoder, const uint8_t *data, size_t size);
/// Fetches the picture produced by the last `vpxdec_decode`. Returns 1 when there is one, 0 when there is not.
int vpxdec_picture(VpxDecoder *decoder, VpxPicture *out);

#endif
