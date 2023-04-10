#version 150 core
// shader loader inserts #defines between the version and this line
#line 3
// gpuinfo says this is supported, so im using it
#extension GL_ARB_separate_shader_objects : require
#extension GL_ARB_explicit_attrib_location : require
// TODO: dont require this, MacOS doesnt support it
#extension GL_ARB_enhanced_layouts : require
#ifdef USE_SSBO
#extension GL_ARB_shader_storage_buffer_object : require
#endif

#ifndef POSITION_LOCATION
#define POSITION_LOCATION 0
#define COLOR_LOCATION 1
#define TEX_COORD_LOCATION 2
#define LIGHTINFO_LOCATION 3
#define WORLD_POSITION_LOCATION 4
#define DYNAMIC_MATRIX_ID_LOCATION 5
#define DYNAMIC_LIGHT_ID_LOCATION 6
// location 7 open
#define STATIC_MATRIX_LOCATION 8
#define STATIC_NORMAL_MATRIX_LOCATION 12
// 16 locations available, so, none left, if more are needed, will need to pack values
// lightInfo and colorIn could be packed together
// light/matrix IDs could be packed together

#define COLOR_OUTPUT 0
#define UV0_OUTPUT 0
#define UV1_OUTPUT 0
#define UV2_OUTPUT 0
#define NORMAL_OUTPUT 0
#define FEEDBACK_BUFFER_STRIDE 0
#endif

#define LIGHTMAP_MULTIPLIER 0.015625 /* 1 / 64 (6 bit) */

// per vertex
layout(location = POSITION_LOCATION) in vec3 position;
layout(location = COLOR_LOCATION) in uint colorIn;
layout(location = TEX_COORD_LOCATION) in vec2 texCoordIn;
layout(location = LIGHTINFO_LOCATION) in uvec2 lightingInfo;

// per instance
layout(location = WORLD_POSITION_LOCATION) in ivec3 worldPosition;
layout(location = DYNAMIC_MATRIX_ID_LOCATION) in int dynamicMatrixID;
layout(location = DYNAMIC_LIGHT_ID_LOCATION) in int dynamicLightID;
layout(location = STATIC_MATRIX_LOCATION) in mat4 staticMatrix;
layout(location = STATIC_NORMAL_MATRIX_LOCATION) in mat4 staticNormalMatrix;

uniform ivec3 playerBlock;
uniform vec3 playerSubBlock;

uniform bool LIGHTING;
uniform bool QUAD;

struct PackedDynamicLightInfo {
// opengl doesnt support smaller types than this, unfortunately
    uint lightingInfo[32];
};

// this is to get around arrays of arrays requirement
struct UnpackedDynamicLightInfoInner {
    uvec2 info[6];
};

struct UnpackedDynamicLightInfo {
    UnpackedDynamicLightInfoInner lighting[8];
};

struct SplitDynamicLightDirectionInfo {
    vec2 directionLight;
    float AO;
};

struct SplitDynamicLightVertexInfo {
    SplitDynamicLightDirectionInfo[6] directionInfo;
};

struct SplitDynamicLightInfo {
    SplitDynamicLightVertexInfo[8] vertexInfo;
};

struct DynamicMatrixPair {
    mat4 modelMatrix;
    mat4 normalMatrix;
};

#ifndef USE_SSBO
uniform samplerBuffer dynamicMatrices;
uniform usamplerBuffer dynamicLights;
#else

layout(std430, binding = 0) buffer dynamicMatrixBuffer {
    DynamicMatrixPair dynamicMatricesSSBO[];
};


layout(std430, binding = 1) buffer dynamicLightBuffer {
    PackedDynamicLightInfo dynamicLightsSSBO[];
};

#endif


layout(xfb_buffer = 0, xfb_stride = FEEDBACK_BUFFER_STRIDE) out TransformFeedback
{
// position is always the first things
    layout(xfb_offset = 0) vec3 positionOutput;

#ifdef COLOR_OUTPUT
    layout(xfb_offset = COLOR_OUTPUT) uint colorOutput;
#endif

#ifdef UV0_OUTPUT
    layout(xfb_offset = UV0_OUTPUT) vec2 textureOutput;
#endif

#ifdef UV1_OUTPUT
    layout(xfb_offset = UV1_OUTPUT) uint overlayOutput;
#endif

#ifdef UV2_OUTPUT
    layout(xfb_offset = UV2_OUTPUT) uint lightmapOutput;
#endif

#ifdef NORMAL_OUTPUT
    layout(xfb_offset = NORMAL_OUTPUT) uint normalOutput;
#endif
};

#ifndef COLOR_OUTPUT
uint colorOutput;
#endif

#ifndef UV0_OUTPUT
vec2 textureOutput;
#endif

#ifndef UV1_OUTPUT
uint overlayOutput;
#endif

#ifndef UV2_OUTPUT
uint lightmapOutput;
#endif

#ifndef NORMAL_OUTPUT
uint normalOutput;
#endif

vec4 lightmapCoords[2];
vec3 cornerLightLevels[8];
const vec3 lightDirections[6] = vec3[6](vec3(1, 0, 0), vec3(0, 1, 0), vec3(0, 0, 1), vec3(-1, 0, 0), vec3(0, -1, 0), vec3(0, 0, -1));

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
    abgr |= (iColor.r & 0xFFu) << 24;
    abgr |= (iColor.g & 0xFFu) << 16;
    abgr |= (iColor.b & 0xFFu) << 8;
    abgr |= (iColor.a & 0xFFu) << 0;
    return abgr;
}

uint packLightPos(vec2 lightPos) {
    lightPos *= 255;
    uvec2 uLightPos = uvec2(lightPos);
    uint packedLightPos = 0u;
    packedLightPos |= (uLightPos.x & 0xFFu);
    packedLightPos |= (uLightPos.y & 0xFFu) << 16;
    return packedLightPos;
}

float cylindrical_distance(vec3 cameraRelativePos);

float calcuateDiffuseMultiplier(vec3 normal);

int extractInt(uint packedint, uint pos, uint width);

uint extractUInt(uint packedint, uint pos, uint width);

UnpackedDynamicLightInfo getLightInfo(int lightID);

SplitDynamicLightInfo splitLightingInfo(UnpackedDynamicLightInfo rawInfo);

void main() {

    colorOutput = 0u;
    textureOutput = vec2(0);
    // no overlay, TODO: support overlay, maybe
    overlayOutput = 0x000A0000u;
    // little endian bullshit
    lightmapOutput = 0x007E0000u;
    normalOutput = 0u;

    mat4 dynamicModelMatrix = mat4(0);
    #ifndef USE_SSBO
    dynamicModelMatrix[0] = texelFetch(dynamicMatrices, dynamicMatrixID * 8 + 0);
    dynamicModelMatrix[1] = texelFetch(dynamicMatrices, dynamicMatrixID * 8 + 1);
    dynamicModelMatrix[2] = texelFetch(dynamicMatrices, dynamicMatrixID * 8 + 2);
    dynamicModelMatrix[3] = texelFetch(dynamicMatrices, dynamicMatrixID * 8 + 3);
    #else
    dynamicModelMatrix = dynamicMatricesSSBO[dynamicMatrixID].modelMatrix;
    #endif

    vec3 floatWorldPosition = vec3(worldPosition - playerBlock) - playerSubBlock;

    mat4 modelMatrix = dynamicModelMatrix * staticMatrix;

    vec4 vertexPosition = modelMatrix * vec4(position, 1.0);
    vec3 vertexModelPos = vertexPosition.xyz;
    vertexPosition += vec4(floatWorldPosition, 0);
    positionOutput = vertexPosition.xyz;

    colorOutput = colorIn;

    textureOutput = texCoordIn;

    vec3 vertexNormal;
    vec2 lightmapCoord;
    if (!QUAD) {
        vertexNormal = normalize(vec3(extractInt(lightingInfo.x, 0u, 16u), extractInt(lightingInfo.y, 16u, 16u), extractInt(lightingInfo.y, 0u, 16u)));
        lightmapCoord = vec2((lightingInfo.x >> 24) & 0xFFu, (lightingInfo.x >> 16) & 0xFFu) * LIGHTMAP_MULTIPLIER;
    } else {
        vertexNormal = normalize(vec3(extractInt(lightingInfo.x, 24u, 4u), extractInt(lightingInfo.x, 28u, 4u), extractInt(lightingInfo.y, 24u, 4u)));
        lightmapCoord = vec2((lightingInfo.y >> 28) & 0x1u, (lightingInfo.y >> 29) & 0x1u);
        lightmapCoords[0].xy = vec2((lightingInfo.x >> 00) & 0x3Fu, (lightingInfo.x >> 06) & 0x3Fu) * LIGHTMAP_MULTIPLIER;
        lightmapCoords[0].zw = vec2((lightingInfo.x >> 12) & 0x3Fu, (lightingInfo.x >> 18) & 0x3Fu) * LIGHTMAP_MULTIPLIER;
        lightmapCoords[1].xy = vec2((lightingInfo.y >> 00) & 0x3Fu, (lightingInfo.y >> 06) & 0x3Fu) * LIGHTMAP_MULTIPLIER;
        lightmapCoords[1].zw = vec2((lightingInfo.y >> 12) & 0x3Fu, (lightingInfo.y >> 18) & 0x3Fu) * LIGHTMAP_MULTIPLIER;
    }
    mat3 dynamicNormalMatrix = mat3(0);
    #ifndef USE_SSBO
    dynamicNormalMatrix[0] = texelFetch(dynamicMatrices, dynamicMatrixID * 8 + 4).xyz;
    dynamicNormalMatrix[1] = texelFetch(dynamicMatrices, dynamicMatrixID * 8 + 5).xyz;
    dynamicNormalMatrix[2] = texelFetch(dynamicMatrices, dynamicMatrixID * 8 + 6).xyz;
    #else
    dynamicNormalMatrix = mat3(dynamicMatricesSSBO[dynamicMatrixID].normalMatrix);
    #endif
    mat3 normalMatrix = dynamicNormalMatrix * mat3(staticNormalMatrix);

    vertexNormal = normalMatrix * vertexNormal;
    ivec3 iVertexNormal = ivec3(vertexNormal * 127);
    normalOutput |= uint(iVertexNormal.z & 0xFF);
    normalOutput <<= 8;
    normalOutput |= uint(iVertexNormal.y & 0xFF);
    normalOutput <<= 8;
    normalOutput |= uint(iVertexNormal.x & 0xFF);

    if (LIGHTING){


        SplitDynamicLightInfo lightingInfo = splitLightingInfo(getLightInfo(dynamicLightID));
        for (int i = 0; i < 8; i++) {
            cornerLightLevels[i] = vec3(0);
            SplitDynamicLightVertexInfo vertexInfo = lightingInfo.vertexInfo[i];
            for (int j = 0; j < 6; j++){
                vec3 lightDirection = lightDirections[j];
                float multiplier = dot(lightDirection, vertexNormal);
                multiplier *= float(multiplier > 0);
                multiplier *= multiplier;

                SplitDynamicLightDirectionInfo directionInfo = vertexInfo.directionInfo[j];
                vec2 directionLight = directionInfo.directionLight;
                float AO = directionInfo.AO;
                cornerLightLevels[i] += vec3(directionLight, AO) * multiplier;
            }
        }

        vec2 lightPos = lightmapCoord;
        if (QUAD) {
            vec2 vert01Avg = lightmapCoords[0].xy * lightmapCoord.x + lightmapCoords[0].zw * (1 - lightmapCoord.x);
            vec2 vert23Avg = lightmapCoords[1].xy * lightmapCoord.x + lightmapCoords[1].zw * (1 - lightmapCoord.x);
            lightPos = vert01Avg * lightmapCoord.y + vert23Avg * (1 - lightmapCoord.y);
        }

        vec3 avgArray[4];
        avgArray[0] = cornerLightLevels[4] * vertexModelPos.z + cornerLightLevels[0] * (1 - vertexModelPos.z);
        avgArray[1] = cornerLightLevels[5] * vertexModelPos.z + cornerLightLevels[1] * (1 - vertexModelPos.z);
        avgArray[2] = cornerLightLevels[6] * vertexModelPos.z + cornerLightLevels[2] * (1 - vertexModelPos.z);
        avgArray[3] = cornerLightLevels[7] * vertexModelPos.z + cornerLightLevels[3] * (1 - vertexModelPos.z);
        avgArray[0] = avgArray[2] * vertexModelPos.y + avgArray[0] * (1 - vertexModelPos.y);
        avgArray[1] = avgArray[3] * vertexModelPos.y + avgArray[1] * (1 - vertexModelPos.y);
        avgArray[0] = avgArray[1] * vertexModelPos.x + avgArray[0] * (1 - vertexModelPos.x);
        lightPos += avgArray[0].xy;

        lightmapOutput = packLightPos(lightPos);

        float AO = avgArray[0].z;
        float AOMultiplier = 1 - AO * .2;
        float diffuseMultiplier = calcuateDiffuseMultiplier(vertexNormal);

        vec4 color = unpackColorABGR(colorIn);
        color = vec4(color.rgb * AOMultiplier * diffuseMultiplier, color.a);
        colorOutput = packColorABGR(color);

    }

    #ifdef SHADERS_LOADED
    colorOutput = 0xFFFFFFFFu;
    #endif
}

float cylindrical_distance(vec3 cameraRelativePos) {
    float distXZ = length(cameraRelativePos.xz);
    float distY = abs(cameraRelativePos.y);
    return max(distXZ, distY);
}

float calcuateDiffuseMultiplier(vec3 normal) {
    vec3 n2 = normal * normal * vec3(.6, .25, .8);
    return min(n2.x + n2.y * (3. + normal.y) + n2.z, 1.);
}

int extractInt(uint packedint, uint pos, uint width) {
    packedint >>= pos;
    uint signBitMask = 1u << (width - 1u);
    uint bitMask = signBitMask - 1u;
    int val = int(~bitMask *  uint((signBitMask & packedint) != 0u));
    val |= int(packedint & bitMask);
    return val;
}

uint extractUInt(uint packedint, uint pos, uint width) {
    packedint >>= pos;
    uint signBitMask = 1u << (width - 1u);
    uint bitMask = signBitMask - 1u;
    return packedint & bitMask;
}

SplitDynamicLightInfo splitLightingInfo(UnpackedDynamicLightInfo rawInfo) {

    SplitDynamicLightInfo info;
    for (int i = 0; i < 8; i++) {
        SplitDynamicLightVertexInfo vertexInfo;
        for (int j = 0; j < 6; j++){
            uvec2 uDirectionLight = rawInfo.lighting[i].info[j];
            vec2 directionLight = vec2(uDirectionLight & 0x3Fu) * LIGHTMAP_MULTIPLIER;
            float AO = uDirectionLight.x >> 6 & 0x3u;

            SplitDynamicLightDirectionInfo directionInfo;
            directionInfo.directionLight = directionLight;
            directionInfo.AO = AO;

            vertexInfo.directionInfo[j] = directionInfo;
        }
        info.vertexInfo[i] = vertexInfo;

    }

    return info;
}

#ifndef USE_SSBO
UnpackedDynamicLightInfo getLightInfo(int lightID) {
    UnpackedDynamicLightInfo info;
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 6; j++){
            info.lighting[i].info[j] = texelFetch(dynamicLights, dynamicLightID * 64 + i * 6 + j).rg;
        }
    }
    return info;
}
#else

UnpackedDynamicLightInfo getLightInfo(int lightID) {
    PackedDynamicLightInfo rawInfo = dynamicLightsSSBO[lightID];

    UnpackedDynamicLightInfo info;
    for (int i = 0; i < 8; i++) {
        for (int j = 0; j < 3; j++){
            for (int k = 0; k < 2; k++) {
                uint rawUInt = rawInfo.lightingInfo[(i * 3) + j];
                uint shiftAmount = uint(k) * 16u;
                uint blocklightInfo = uint(rawUInt >> shiftAmount);
                uint skyLightInfo = uint(rawUInt >> (shiftAmount + 8u));

                info.lighting[i].info[j * 2 + k] = uvec2(blocklightInfo, skyLightInfo);
            }
        }
    }
    return info;
}
#endif
