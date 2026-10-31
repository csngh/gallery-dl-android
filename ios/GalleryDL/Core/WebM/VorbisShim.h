#ifndef VorbisShim_h
#define VorbisShim_h

#include <stddef.h>
#include <stdint.h>

/// A thin layer over libvorbis's decoder. A WebM file holds bare Vorbis packets with no Ogg container, so the shim hands them
/// to libvorbis one at a time and returns the sound as interleaved 32-bit float samples.
typedef struct VorbisDecoder VorbisDecoder;

/// Starts a decoder from the track's codec data (the three Vorbis headers, in Matroska's lacing). Returns NULL when they
/// cannot be read. `channels` and `sampleRate` are filled in on success.
VorbisDecoder *vorbisdec_create(const uint8_t *codecData, size_t size, int *channels, long *sampleRate);
void vorbisdec_destroy(VorbisDecoder *decoder);
/// Forgets what came before, for after a seek.
void vorbisdec_reset(VorbisDecoder *decoder);
/// Decodes one packet. Writes up to `capacity` frames to `out` and returns how many (0 when the packet gives no sound yet,
/// which is normal for the first packet after a reset), or -1 when the packet is damaged.
int vorbisdec_decode(VorbisDecoder *decoder, const uint8_t *packet, size_t size, float *out, int capacity);

#endif
