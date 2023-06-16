#line 2

#ifndef QUARTZ_INSERT_DEFINES
#define POSITION_LOCATION 0
#define COLOR_LOCATION 1
#define TEX_COORD_LOCATION 2
#define NORMAL_LOCATION 3
#define WORLD_POSITION_LOCATION 4
#define DYNAMIC_MATRIX_ID_LOCATION 5
// location 6 open
// location 7 open
#define STATIC_MATRIX_LOCATION 8
#define STATIC_NORMAL_MATRIX_LOCATION 12
// location 15 open
// 16 locations available, so, none left, if more are needed, will need to pack values
// lightInfo and colorIn could be packed together
// light/matrix IDs could be packed together

#define COLOR_OUTPUT
#define NORMAL_OUTPUT
#define TEXTURE_OUTPUT
#define OVERLAY_OUTPUT
#define LIGHTMAP_OUTPUT
#endif

vec4 unpackColorABGR(uint color);

uint packColorABGR(vec4 color);

uint packNormal(vec3 normal);

int getLightChunkIndex(ivec3 blockPos);

// per vertex
layout(location = POSITION_LOCATION) in vec3 position;
layout(location = COLOR_LOCATION) in uint colorIn;
layout(location = TEX_COORD_LOCATION) in vec2 texCoordIn;
layout(location = NORMAL_LOCATION) in vec3 normalIn;

// per instance
layout(location = WORLD_POSITION_LOCATION) in ivec3 worldPosition;
layout(location = STATIC_MATRIX_LOCATION) in mat4 modelMatrix;
layout(location = STATIC_NORMAL_MATRIX_LOCATION) in mat3 normalMatrix;

layout(std140) uniform MainUBO {
    ivec4 playerBlock;
    vec4 playerSubBlock;
    ivec3 lightCunkLookupOffset;
    int irisShadersEnabled;
};


uniform usamplerBuffer intermediateLightChunkIndexLookup;

// im trusting the linker to yeet as much of the code as it can
// it probably will
out vec3 positionOutput;
out uint normalOutput;
out uint colorOutput;
out vec2 textureOutput;
out uint overlayOutput;
out uint lightmapOutput;

void main() {
    ivec3 worldTransform = worldPosition - playerBlock.xyz;

    // any transforms that touch W will be lost, potentailly do the division on that?
    // dynamic matrices are handled in a compute pre-step
    vec3 transformedModelPos = (modelMatrix * vec4(position, 1)).xyz;
    vec3 normal = normalize(normalMatrix * normalIn);
    positionOutput = transformedModelPos + vec3(worldTransform);
    normalOutput = packNormal(normal);
    colorOutput = colorIn;
    textureOutput = texCoordIn;
    // TODO: proper handling of overlay values
    overlayOutput = uint(0 * 15.0) | ((uint(int(bool(true))) * 7u + 3u) << 16);

    ivec3 modelIntPos = ivec3(floor(transformedModelPos));
    vec3 modelSubBlockPos = transformedModelPos - modelIntPos;

    ivec3 actualWorldBlockPos = modelIntPos + worldPosition;

    lightmapOutput = uint(getLightChunkIndex(actualWorldBlockPos)) | (1u << 30);
}

vec4 unpackColorABGR(uint color) {
    uint a = (color >> 24) & 0xFFu;
    uint b = (color >> 16) & 0xFFu;
    uint g = (color >> 8) & 0xFFu;
    uint r = (color >> 0) & 0xFFu;
    return vec4(r, g, b, a) / 255;
}

uint packColorABGR(vec4 color) {
    color *= 255;
    uvec4 iColor = uvec4(color);
    uint abgr = 0u;
    abgr |= (iColor.a & 0xFFu) << 24;
    abgr |= (iColor.b & 0xFFu) << 16;
    abgr |= (iColor.g & 0xFFu) << 8;
    abgr |= (iColor.r & 0xFFu) << 0;
    return abgr;
}

uint packNormal(vec3 normal){
    normal = normalize(normal);
    uint packedNormal = 0u;
    packedNormal |= uint(int(normal.x * 127.0) & 0xFF);
    packedNormal |= uint(int(normal.y * 127.0) & 0xFF) << 8;
    packedNormal |= uint(int(normal.z * 127.0) & 0xFF) << 16;
    return packedNormal;
}


int getLightChunkIndex(ivec3 blockPos) {

    ivec3 worldChunk = blockPos >> 4;

    ivec3 lookupChunk = worldChunk - lightCunkLookupOffset.xyz;

    if (lookupChunk.x < 0 || lookupChunk.y < 0 || lookupChunk.z < 0 || lookupChunk.x >= 64 || lookupChunk.y >= 24 || lookupChunk.z >= 64) {
        return -1;
    }

    int lookupIndex = (((lookupChunk.z * 24) + lookupChunk.y) * 64) + lookupChunk.x;
    return int(texelFetch(intermediateLightChunkIndexLookup, lookupIndex).r);
}

