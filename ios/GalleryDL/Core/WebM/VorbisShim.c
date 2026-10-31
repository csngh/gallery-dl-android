#include "VorbisShim.h"

#include <stdlib.h>
#include <string.h>
#include <vorbis/codec.h>

struct VorbisDecoder {
    vorbis_info info;
    vorbis_comment comment;
    vorbis_dsp_state dsp;
    vorbis_block block;
    int channels;
};

/// Matroska stores the three headers in one block: a count byte, the sizes of the first two (each a run of 255s ended by a
/// smaller byte), then the headers back to back.
static int split_headers(const uint8_t *data, size_t size, const uint8_t *parts[3], size_t lengths[3]) {
    if (size < 3 || data[0] != 2) return 0;
    size_t position = 1;
    for (int i = 0; i < 2; i++) {
        size_t length = 0;
        while (position < size && data[position] == 255) { length += 255; position++; }
        if (position >= size) return 0;
        length += data[position++];
        lengths[i] = length;
    }
    if (lengths[0] + lengths[1] > size - position) return 0;
    lengths[2] = size - position - lengths[0] - lengths[1];
    parts[0] = data + position;
    parts[1] = parts[0] + lengths[0];
    parts[2] = parts[1] + lengths[1];
    return 1;
}

VorbisDecoder *vorbisdec_create(const uint8_t *codecData, size_t size, int *channels, long *sampleRate) {
    const uint8_t *parts[3];
    size_t lengths[3];
    if (!codecData || !split_headers(codecData, size, parts, lengths)) return NULL;

    VorbisDecoder *decoder = calloc(1, sizeof(VorbisDecoder));
    if (!decoder) return NULL;
    vorbis_info_init(&decoder->info);
    vorbis_comment_init(&decoder->comment);

    for (int i = 0; i < 3; i++) {
        ogg_packet packet;
        memset(&packet, 0, sizeof(packet));
        packet.packet = (unsigned char *)parts[i];
        packet.bytes = (long)lengths[i];
        packet.b_o_s = i == 0;
        packet.packetno = i;
        if (vorbis_synthesis_headerin(&decoder->info, &decoder->comment, &packet) != 0) goto fail;
    }
    if (vorbis_synthesis_init(&decoder->dsp, &decoder->info) != 0) goto fail;
    if (vorbis_block_init(&decoder->dsp, &decoder->block) != 0) { vorbis_dsp_clear(&decoder->dsp); goto fail; }

    decoder->channels = decoder->info.channels;
    *channels = decoder->info.channels;
    *sampleRate = decoder->info.rate;
    return decoder;

fail:
    vorbis_comment_clear(&decoder->comment);
    vorbis_info_clear(&decoder->info);
    free(decoder);
    return NULL;
}

void vorbisdec_destroy(VorbisDecoder *decoder) {
    if (!decoder) return;
    vorbis_block_clear(&decoder->block);
    vorbis_dsp_clear(&decoder->dsp);
    vorbis_comment_clear(&decoder->comment);
    vorbis_info_clear(&decoder->info);
    free(decoder);
}

void vorbisdec_reset(VorbisDecoder *decoder) {
    if (decoder) vorbis_synthesis_restart(&decoder->dsp);
}

int vorbisdec_decode(VorbisDecoder *decoder, const uint8_t *data, size_t size, float *out, int capacity) {
    if (!decoder || !data || size == 0) return -1;
    ogg_packet packet;
    memset(&packet, 0, sizeof(packet));
    packet.packet = (unsigned char *)data;
    packet.bytes = (long)size;
    if (vorbis_synthesis(&decoder->block, &packet) != 0) return -1;
    if (vorbis_synthesis_blockin(&decoder->dsp, &decoder->block) != 0) return -1;

    int total = 0;
    float **pcm;
    int frames;
    while ((frames = vorbis_synthesis_pcmout(&decoder->dsp, &pcm)) > 0) {
        int take = frames < capacity - total ? frames : capacity - total;
        if (take <= 0) break;
        for (int c = 0; c < decoder->channels; c++) {
            float *source = pcm[c];
            float *target = out + (size_t)total * decoder->channels + c;
            for (int i = 0; i < take; i++) target[(size_t)i * decoder->channels] = source[i];
        }
        total += take;
        vorbis_synthesis_read(&decoder->dsp, take);
    }
    return total;
}
