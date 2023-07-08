#version 330 core

#define LIGHTMAP_MULTIPLIER 0.015625 /* 1 / 64 (6 bit) */

layout(location = 0) in vec3 positionInput;
layout(location = 1) in vec3 normalInput;
layout(location = 2) in uint colorInput;
layout(location = 3) in vec2 textureInput;
layout(location = 4) in uint overlayInput;
layout(location = 5) in uint lightmapInput;

out vec3 positionOutput;
out uint normalOutput;
out uint colorOutput;
out vec2 textureOutput;
out uint overlayOutput;
out uint lightmapOutput;

layout(std140) uniform MainUBO {
    ivec4 playerBlock;
    vec4 playerSubBlock;
    ivec3 lightCunkLookupOffset;
    int irisShadersEnabled;
};

uniform uint activeArrayLayer;
uniform usampler2DArray intermediateLightDataTexture[6];

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

uint packLightPos(vec2 lightPos);

uint packNormal(vec3 normal){
    normal = normalize(normal);
    uint packedNormal = 0u;
    packedNormal |= uint(int(normal.x * 127.0) & 0xFF);
    packedNormal |= uint(int(normal.y * 127.0) & 0xFF) << 8;
    packedNormal |= uint(int(normal.z * 127.0) & 0xFF) << 16;
    return packedNormal;
}


float int8SNormToFloat(int num){
     float val = num & 0x7F;
    val /= 127.0;
    val *= -((num >> 8) & 1);
    return val;
}

vec3 unpackNormal(uint packedNormal) {
    vec3 normal;
    normal.x = int8SNormToFloat(int(packedNormal) * 0xFF);
    normal.y = int8SNormToFloat((int(packedNormal) >> 8) * 0xFF);
    normal.z = int8SNormToFloat((int(packedNormal) >> 16) * 0xFF);
    return normalize(normal);
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

void main() {
    vec3 normal = normalize(normalInput);

    positionOutput = positionInput;
    normalOutput = packNormal(normal);
    colorOutput = colorInput;
    textureOutput = textureInput;
    overlayOutput = overlayInput;
    lightmapOutput = lightmapInput;

    if((lightmapInput & (1u << 30)) == 0u){
        return;
    }
    if (int(lightmapOutput) < 0){
        return;
    }
    // 16 layer blocks are done each pass
    if ((lightmapInput & 0x3F0u) != activeArrayLayer){
        return;
    }

    positionOutput = positionInput - playerSubBlock.xyz;

    ivec3 integerPosition = ivec3(floor(positionInput));
    vec3 floatPosition = positionInput + integerPosition;

    vec3 absPos = abs(positionInput);

//    ivec3 actualWorldBlockPos = ivec3(ceil(absPos)) * ivec3(sign(positionInput)) + playerBlock.xyz;
    ivec3 actualWorldBlockPos = integerPosition + playerBlock.xyz;
    //actualWorldBlockPos += ivec3(notEqual(actualWorldBlockPos, ivec3(-1)));

    vec3 modelSubBlockPos = positionInput;
    modelSubBlockPos -= floor(modelSubBlockPos);

    SplitDynamicLightInfo rawLightInfo = loadLightingInfo(actualWorldBlockPos);
    DynamicLightingIntermediate intermediateLightInfo = calculateDynamicLightingIntermediate(rawLightInfo, normal);
    DynamicLightingOutput lightingInfo = calculateDynamicLighting(intermediateLightInfo, modelSubBlockPos);

    lightmapOutput = packLightPos(vec2(lightingInfo.lightmap));
    vec4 unpackedColor = unpackColorABGR(colorInput);
    unpackedColor *= lightingInfo.colorMultiplier;
    colorOutput = packColorABGR(unpackedColor);
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
    lightAO.x = (packedInt >> 6) & 0x3Fu;
    lightAO.y = (packedInt) & 0x3Fu;
    lightAO.z = (packedInt >> 12) & 0x3u;
    return lightAO;
}


uint packLightPos(vec2 lightPos) {
    lightPos *= 255;
    uvec2 uLightPos = uvec2(lightPos);
    uint packedLightPos = 0u;
    packedLightPos |= (uLightPos.x & 0xFFu);
    packedLightPos |= (uLightPos.y & 0xFFu) << 16;
    return packedLightPos;
}

SplitDynamicLightVertexInfo getLightVertexInfo(ivec3 baseTexel, ivec3 lightChunkPos){
    ivec3 lookupTexel = baseTexel + ivec3(lightChunkPos.x, lightChunkPos.y + lightChunkPos.z * 18, 0);
    SplitDynamicLightVertexInfo toReturn;
    {
        uint packedData = texelFetch(intermediateLightDataTexture[0], lookupTexel, 0).r;
        uvec3 unpackedData = unpackLightAOuint16(packedData);
        toReturn.directionInfo[0].directionLight = vec2(unpackedData.xy) * LIGHTMAP_MULTIPLIER;
        toReturn.directionInfo[0].AO = unpackedData.z;
    }
    {
        uint packedData = texelFetch(intermediateLightDataTexture[1], lookupTexel, 0).r;
        uvec3 unpackedData = unpackLightAOuint16(packedData);
        toReturn.directionInfo[1].directionLight = vec2(unpackedData.xy) * LIGHTMAP_MULTIPLIER;
        toReturn.directionInfo[1].AO = unpackedData.z;
    }
    {
        uint packedData = texelFetch(intermediateLightDataTexture[2], lookupTexel, 0).r;
        uvec3 unpackedData = unpackLightAOuint16(packedData);
        toReturn.directionInfo[2].directionLight = vec2(unpackedData.xy) * LIGHTMAP_MULTIPLIER;
        toReturn.directionInfo[2].AO = unpackedData.z;
    }
    {
        uint packedData = texelFetch(intermediateLightDataTexture[3], lookupTexel, 0).r;
        uvec3 unpackedData = unpackLightAOuint16(packedData);
        toReturn.directionInfo[3].directionLight = vec2(unpackedData.xy) * LIGHTMAP_MULTIPLIER;
        toReturn.directionInfo[3].AO = unpackedData.z;
    }
    {
        uint packedData = texelFetch(intermediateLightDataTexture[4], lookupTexel, 0).r;
        uvec3 unpackedData = unpackLightAOuint16(packedData);
        toReturn.directionInfo[4].directionLight = vec2(unpackedData.xy) * LIGHTMAP_MULTIPLIER;
        toReturn.directionInfo[4].AO = unpackedData.z;
    }
    {
        uint packedData = texelFetch(intermediateLightDataTexture[5], lookupTexel, 0).r;
        uvec3 unpackedData = unpackLightAOuint16(packedData);
        toReturn.directionInfo[5].directionLight = vec2(unpackedData.xy) * LIGHTMAP_MULTIPLIER;
        toReturn.directionInfo[5].AO = unpackedData.z;
    }
    return toReturn;
}


SplitDynamicLightInfo loadLightingInfo(ivec3 blockPos) {

    SplitDynamicLightInfo toReturn;
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 8; j++) {
            toReturn.vertexInfo[i].directionInfo[j].directionLight = vec2(0);
        }
    }

    int lightChunkIndex = int(lightmapInput);
    if (lightChunkIndex < 0){
        return toReturn;
    }

    ivec3 worldChunk = blockPos >> 4;
    ivec3 subChunkPos = blockPos - (worldChunk << 4);

    ivec3 lightChunkBaseTexel = ivec3((lightChunkIndex >> 11) & 0x1F, (lightChunkIndex >> 10) & 0x1, lightChunkIndex & 0xF) * ivec3(18, 320, 1);
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
    outputs.colorMultiplier = vec4(totalColorMultiplier, totalColorMultiplier, totalColorMultiplier, 1);
    return outputs;
}