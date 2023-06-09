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

#define LIGHTMAP_MULTIPLIER 0.015625 /* 1 / 64 (6 bit) */

struct SplitDynamicLightDirectionInfo {
    vec2 directionLight;
    float AO;
};

/*
 * 6 face directions per vertex
 * inward lighting is considered too
 * [0]: +X
 * [1]: +Y
 * [2]: +Z
 * [3]: -X
 * [4]: -Y
 * [5]: -Z
 */
const vec3 lightDirections[6] = vec3[6](vec3(1, 0, 0), vec3(0, 1, 0), vec3(0, 0, 1), vec3(-1, 0, 0), vec3(0, -1, 0), vec3(0, 0, -1));
struct SplitDynamicLightVertexInfo {
    SplitDynamicLightDirectionInfo directionInfo[6];
};

/*
 * 8 vertices of the cube, this order
 * [0]: (0, 0, 0)
 * [1]: (1, 0, 0)
 * [2]: (0, 1, 0)
 * [3]: (1, 1, 0)
 * [4]: (0, 0, 1)
 * [5]: (1, 0, 1)
 * [6]: (0, 1, 1)
 * [7]: (1, 1, 1)
 */
const ivec3 lightPositions[8] = ivec3[8](ivec3(0, 0, 0), ivec3(1, 0, 0), ivec3(0, 1, 0), ivec3(1, 1, 0), ivec3(0, 0, 1), ivec3(1, 0, 1), ivec3(0, 1, 1), ivec3(1, 1, 1));
struct SplitDynamicLightInfo {
    SplitDynamicLightVertexInfo vertexInfo[8];
};


struct DynamicLightingIntermediate {
    vec3 cornerLightLevels[8];
    float diffuse;
};

struct DynamicLightingOutput {
    vec2 lightmap;
    vec4 colorMultiplier;
};

/*
 * X0: 0, 2, 3, 6; X1: 1, 3, 5, 7
 * Y0: 0, 1, 4, 5; Y1: 2, 3, 6, 7
 * Z0: 0, 1, 2, 3; Z1: 4, 5, 6, 7
 *
 * [0]: (0, 0, 0)
 * [1]: (1, 0, 0)
 * [2]: (0, 1, 0)
 * [3]: (1, 1, 0)
 * [4]: (0, 0, 1)
 * [5]: (1, 0, 1)
 * [6]: (0, 1, 1)
 * [7]: (1, 1, 1)
 *
 * position must be in the range of 0-1
 */
vec3 average3D(vec3 vals[8], vec3 position);

SplitDynamicLightInfo loadLightingInfo(ivec3 blockPos);

DynamicLightingIntermediate calculateDynamicLightingIntermediate(SplitDynamicLightInfo lightingInfo, vec3 normal);

DynamicLightingOutput calculateDynamicLighting(DynamicLightingIntermediate intermediate, vec3 position);

struct DynamicMatrixPair {
    mat4 modelMatrix;
    mat3 normalMatrix;
};

vec4 unpackColorABGR(uint color);

uint packColorABGR(vec4 color);

DynamicMatrixPair getDynamicMatrix(int matrixID);

uint packNormal(vec3 normal);

uint packLightPos(vec2 lightPos);

// per vertex
layout(location = POSITION_LOCATION) in vec3 position;
layout(location = COLOR_LOCATION) in uint colorIn;
layout(location = TEX_COORD_LOCATION) in vec2 texCoordIn;
layout(location = NORMAL_LOCATION) in vec3 normalIn;

// per instance
layout(location = WORLD_POSITION_LOCATION) in ivec3 worldPosition;
layout(location = DYNAMIC_MATRIX_ID_LOCATION) in int dynamicMatrixID;
layout(location = STATIC_MATRIX_LOCATION) in mat4 modelMatrix;
layout(location = STATIC_NORMAL_MATRIX_LOCATION) in mat3 normalMatrix;

layout(std140, binding = 0) uniform MainUBO {
    ivec4 playerBlock;
    vec4 playerSubBlock;
    ivec3 lightCunkLookupOffset;
    int irisShadersEnabled;
};


layout(binding = 1) uniform usamplerBuffer intermediateLightChunkIndexLookup;
layout(binding = 2) uniform usampler2DArray intermediateLightDataTexture[6];

// im trusting the linker to yeet as much of the code as it can
// it probably will
out vec3 positionOutput;
out uint normalOutput;
out uint colorOutput;
out vec2 textureOutput;
out uint overlayOutput;
out uint lightmapOutput;

void main() {
    vec3 worldTransform = vec3(worldPosition - playerBlock.xyz) - playerSubBlock.xyz;

    // any transforms that touch W will be lost, potentailly do the division on that?
    // dynamic matrices are handled in a compute pre-step
    vec3 transformedModelPos = (modelMatrix * vec4(position, 1)).xyz;
    vec3 normal = normalize(normalMatrix * normalIn);
    positionOutput = transformedModelPos + worldTransform;
    normalOutput = packNormal(normal);
    textureOutput = texCoordIn;
    // TODO: proper handling of overlay values
    overlayOutput = uint(0 * 15.0) | ((uint(int(bool(true))) * 7u + 3u) << 16);

    ivec3 modelIntPos = ivec3(transformedModelPos);
    vec3 modelSubBlockPos = transformedModelPos - modelIntPos;

    ivec3 actualWorldBlockPos = modelIntPos + worldPosition;

    SplitDynamicLightInfo rawLightInfo = loadLightingInfo(actualWorldBlockPos);
    DynamicLightingIntermediate intermediateLightInfo = calculateDynamicLightingIntermediate(rawLightInfo, normal);
    DynamicLightingOutput lightingInfo = calculateDynamicLighting(intermediateLightInfo, modelSubBlockPos);

    lightmapOutput = packLightPos(lightingInfo.lightmap);

    vec4 unpackedColor = unpackColorABGR(colorIn);
    unpackedColor *= lightingInfo.colorMultiplier;
    colorOutput = packColorABGR(unpackedColor);

    //    vec3 normalToColorize = normalize(normal);
    //    colorOutput = packColorABGR(vec4(normalToColorize, 1));
    //    lightmapOutput = packLightPos(vec2(7, 7));
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

struct DynamicMatrixSSBOPair {
    mat4 modelMatrix;
    mat4 normalMatrix;
};

layout(std430, binding = 0) buffer dynamicMatrixBuffer {
    DynamicMatrixSSBOPair dynamicMatricesSSBO[];
};

DynamicMatrixPair getDynamicMatrix(int matrixID) {
    DynamicMatrixSSBOPair ssboPair = dynamicMatricesSSBO[matrixID];

    DynamicMatrixPair pair;
    pair.modelMatrix = ssboPair.modelMatrix;
    pair.normalMatrix = mat3(ssboPair.normalMatrix);

    return pair;
}

uint packNormal(vec3 normal){
    normal = normalize(normal);
    uint packedNormal = 0;
    packedNormal |= uint(int(normal.x * 127.0) & 0xFF);
    packedNormal |= uint(int(normal.y * 127.0) & 0xFF) << 8;
    packedNormal |= uint(int(normal.z * 127.0) & 0xFF) << 16;
    return packedNormal;
}

uint packLightPos(vec2 lightPos) {
    lightPos *= 255;
    uvec2 uLightPos = uvec2(lightPos);
    uint packedLightPos = 0u;
    packedLightPos |= (uLightPos.x & 0xFFu);
    packedLightPos |= (uLightPos.y & 0xFFu) << 16;
    return packedLightPos;
}

float calcuateDiffuseMultiplier(vec3 normal) {
    vec3 n2 = normal * normal * vec3(.6, .25, .8);
    return min(n2.x + n2.y * (3. + normal.y) + n2.z, 1.);
}

vec3 average3D(vec3 vals[8], vec3 position) {

    vec3 zAverages[4];
    zAverages[0] = vals[4] * position.z + vals[0] * (1 - position.z);
    zAverages[1] = vals[5] * position.z + vals[1] * (1 - position.z);
    zAverages[2] = vals[6] * position.z + vals[2] * (1 - position.z);
    zAverages[3] = vals[7] * position.z + vals[3] * (1 - position.z);

    vec3 zyAverges[2];
    zyAverges[0] = zAverages[2] * position.y + zAverages[0] * (1 - position.y);
    zyAverges[1] = zAverages[3] * position.y + zAverages[1] * (1 - position.y);

    vec3 zyxAverage = zyAverges[1] * position.x + zyAverges[0] * (1 - position.x);

    return zyxAverage;
}

uvec3 unpackLightAOuint16(uint packedInt) {
    uvec3 lightAO;
    packedInt &= 0xFFFFu;
    lightAO.x = (packedInt  >> 6) & 0x3Fu;
    lightAO.y = packedInt & 0x3Fu;
    lightAO.z = (packedInt  >> 12) & 0x3u;
    return lightAO;
}

SplitDynamicLightVertexInfo getLightVertexInfo(ivec3 baseTexel, ivec3 lightChunkPos){
    ivec3 lookupTexel = baseTexel + ivec3(lightChunkPos.x, lightChunkPos.y + lightChunkPos.z * 17, 0);
    SplitDynamicLightVertexInfo toReturn;
    for (int i = 0; i < 6; i++) {
        uint packedData = texelFetch(intermediateLightDataTexture[i], lookupTexel, 0).r;
        uvec3 unpackedData = unpackLightAOuint16(packedData);
        toReturn.directionInfo[i].directionLight = vec2(unpackedData.xy) * LIGHTMAP_MULTIPLIER;
        toReturn.directionInfo[i].AO = unpackedData.z;
    }
    return toReturn;
}

SplitDynamicLightInfo loadLightingInfo(ivec3 blockPos) {
    ivec3 worldChunk = blockPos >> 4;
    ivec3 subChunkPos = blockPos - (worldChunk << 4);

    ivec3 lookupChunk = worldChunk - lightCunkLookupOffset.xyz;
    SplitDynamicLightInfo toReturn;
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            toReturn.vertexInfo[i].directionInfo[j].directionLight = vec2(0);
        }
    }

    if (lookupChunk.x < 0 || lookupChunk.y < 0 || lookupChunk.z < 0 || lookupChunk.x >= 64 || lookupChunk.y >= 24 || lookupChunk.z >= 64) {
        // out of bounds lookup, flaged as red in overlay
        overlayOutput = uint(0 * 15.0) | ((uint(int(bool(false))) * 7u + 3u) << 16);
        return toReturn;
    }

    int lookupIndex = (((lookupChunk.z * 24) + lookupChunk.y) * 64) + lookupChunk.x;
    int lightChunkIndex = int(texelFetch(intermediateLightChunkIndexLookup, lookupIndex).r);
    ivec3 lightChunkBaseTexel = ivec3((lightChunkIndex >> 11) & 0x1F, (lightChunkIndex >> 10) & 0x1, lightChunkIndex & 0x3FF) * ivec3(17, 320, 1);

    for (int i = 0; i < 8; i++) {
        toReturn.vertexInfo[i] = getLightVertexInfo(lightChunkBaseTexel, subChunkPos + lightPositions[i]);
    }

    return toReturn;
}

DynamicLightingIntermediate calculateDynamicLightingIntermediate(SplitDynamicLightInfo lightingInfo, vec3 normal) {
    DynamicLightingIntermediate intermediate;

    for (int i = 0; i < 8; i++) {
        intermediate.cornerLightLevels[i] = vec3(0);
        SplitDynamicLightVertexInfo vertexInfo = lightingInfo.vertexInfo[i];
        for (int j = 0; j < 6; j++){
            vec3 lightDirection = lightDirections[j];
            float multiplier = dot(lightDirection, normal);
            multiplier *= float(multiplier > 0);
            multiplier *= multiplier;

            SplitDynamicLightDirectionInfo directionInfo = vertexInfo.directionInfo[j];
            vec2 directionLight = directionInfo.directionLight;
            float AO = directionInfo.AO;
            intermediate.cornerLightLevels[i] += vec3(directionLight, AO) * multiplier;
        }
    }

    intermediate.diffuse = calcuateDiffuseMultiplier(normal);

    return intermediate;
}

DynamicLightingOutput calculateDynamicLighting(DynamicLightingIntermediate intermediate, vec3 position) {

    vec3 averaged = average3D(intermediate.cornerLightLevels, position);

    float ambientOcclusion = 1 - averaged.z * 0.2;
    float totalColorMultiplier = ambientOcclusion;
    if (!bool(irisShadersEnabled)) {
        totalColorMultiplier *= intermediate.diffuse;
    }

    DynamicLightingOutput outputs;
    outputs.lightmap = averaged.xy;
    outputs.colorMultiplier = vec4(totalColorMultiplier.xxx, 1);
    return outputs;
}