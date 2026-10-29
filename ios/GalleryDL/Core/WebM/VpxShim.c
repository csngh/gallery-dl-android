#include "VpxShim.h"

#include <stdlib.h>
#include <vpx/vp8dx.h>
#include <vpx/vpx_decoder.h>

struct VpxDecoder {
    vpx_codec_ctx_t context;
    vpx_codec_iter_t iterator;
};

VpxDecoder *vpxdec_create(int isVP9, int threads) {
    VpxDecoder *decoder = calloc(1, sizeof(VpxDecoder));
    if (!decoder) return NULL;
    vpx_codec_dec_cfg_t config = {0};
    config.threads = threads > 0 ? (unsigned int)threads : 1;
    vpx_codec_iface_t *iface = isVP9 ? vpx_codec_vp9_dx() : vpx_codec_vp8_dx();
    if (vpx_codec_dec_init(&decoder->context, iface, &config, 0) != VPX_CODEC_OK) {
        free(decoder);
        return NULL;
    }
    return decoder;
}

void vpxdec_destroy(VpxDecoder *decoder) {
    if (!decoder) return;
    vpx_codec_destroy(&decoder->context);
    free(decoder);
}

int vpxdec_decode(VpxDecoder *decoder, const uint8_t *data, size_t size) {
    decoder->iterator = NULL;
    return vpx_codec_decode(&decoder->context, data, (unsigned int)size, NULL, 0) == VPX_CODEC_OK ? 0 : 1;
}

int vpxdec_picture(VpxDecoder *decoder, VpxPicture *out) {
    vpx_image_t *image = vpx_codec_get_frame(&decoder->context, &decoder->iterator);
    // Only the usual 8-bit 4:2:0 pictures are handled; anything else is treated as undecodable.
    if (!image || image->fmt != VPX_IMG_FMT_I420) return 0;
    out->width = (int)image->d_w;
    out->height = (int)image->d_h;
    for (int i = 0; i < 3; i++) {
        out->planes[i] = image->planes[i];
        out->strides[i] = image->stride[i];
    }
    out->fullRange = image->range == VPX_CR_FULL_RANGE;
    out->colorSpace = image->cs == VPX_CS_BT_601 ? 1 : (image->cs == VPX_CS_BT_709 ? 2 : 0);
    return 1;
}
