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
const vec3 lightPositions[8] = vec3[8](vec3(0, 0, 0), vec3(1, 0, 0), vec3(0, 1, 0), vec3(1, 1, 0), vec3(0, 0, 1), vec3(1, 0, 1), vec3(0, 1, 1), vec3(1, 1, 1));
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

struct Uniforms {
    vec3 worldTransform;

    mat4 modelMatrix;
    mat3 modelNormalMatrix;

    bool doDynamicLighting;
    SplitDynamicLightInfo lightingInfo;
};

struct VertIn {
    vec3 position;
    vec3 normal;
    vec4 color;
    vec2 texture;
    bool whiteOverlay;
    float overlay;
    vec2 lightmap;
};

struct VertOut {
    vec3 position;
    vec3 normal;
    vec4 color;
    vec2 texture;
    bool whiteOverlay;
    float overlay;
    vec2 lightmap;
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

DynamicLightingIntermediate calculateDynamicLightingIntermediate(SplitDynamicLightInfo lightingInfo, vec3 normal);

DynamicLightingOutput calculateDynamicLighting(DynamicLightingIntermediate intermediate, vec3 position);

VertOut vert(VertIn inputs, Uniforms uniforms) {
    VertOut outputs;

    outputs.position = (uniforms.modelMatrix * vec4(inputs.position, 1)).xyz + uniforms.worldTransform;
    outputs.normal = uniforms.modelNormalMatrix * inputs.normal;
    outputs.color = inputs.color;
    outputs.texture = inputs.texture;
    outputs.whiteOverlay = inputs.whiteOverlay;
    outputs.overlay = inputs.overlay;
    outputs.lightmap = inputs.lightmap;

    if (uniforms.doDynamicLighting) {
        // these two stages would normally be done in different shaders, but this is feeding to B3D, so, all done here
        DynamicLightingOutput dynamicLighting = calculateDynamicLighting(calculateDynamicLightingIntermediate(uniforms.lightingInfo, outputs.normal), outputs.position);
        outputs.color *= dynamicLighting.colorMultiplier;
        outputs.lightmap += dynamicLighting.lightmap;
    }

    return outputs;
}

#ifndef QUARTZ_INSERT_DEFINES
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

// this is to get around arrays of arrays requirement
struct UnpackedDynamicLightInfoInner {
    uvec2 info[6];
};

struct UnpackedDynamicLightInfo {
    UnpackedDynamicLightInfoInner lighting[8];
};

struct DynamicMatrixPair {
    mat4 modelMatrix;
    mat3 normalMatrix;
};

vec4 unpackColorABGR(uint color);

uint packColorABGR(vec4 color);

uint packLightPos(vec2 lightPos);

float cylindrical_distance(vec3 cameraRelativePos);

float calcuateDiffuseMultiplier(vec3 normal);

int extractInt(uint packedint, uint pos, uint width);

uint extractUInt(uint packedint, uint pos, uint width);

DynamicMatrixPair getDynamicMatrix(int matrixID);

UnpackedDynamicLightInfo getLightInfo(int lightID);

SplitDynamicLightInfo splitLightingInfo(UnpackedDynamicLightInfo rawInfo);


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

// TODO: UBOs
uniform ivec3 playerBlock;
uniform vec3 playerSubBlock;

uniform bool LIGHTING;
uniform bool QUAD;

// unfortunately, apple exists, and doesnt support GL_ARB_enhanced_layouts
out vec3 positionOutput;
out uint normalOutput;
out uint colorOutput;
out vec2 textureOutput;
out uint overlayOutput;
out uint lightmapOutput;

void main() {
    Uniforms uniforms;

    uniforms.worldTransform = vec3(worldPosition - playerBlock) - playerSubBlock;

    // TODO: potentially compute shader or additional feedback stage this, its the same for every vertex within an instance
    DynamicMatrixPair dynamicMatrices = getDynamicMatrix(dynamicMatrixID);
    uniforms.modelMatrix = dynamicMatrices.modelMatrix * staticMatrix;
    uniforms.modelNormalMatrix = dynamicMatrices.normalMatrix * mat3(staticNormalMatrix);

    uniforms.doDynamicLighting = LIGHTING;
    uniforms.lightingInfo = splitLightingInfo(getLightInfo(dynamicLightID));

    VertIn inputs;
    inputs.position = position;
    if (!QUAD) {
        inputs.normal = normalize(vec3(extractInt(lightingInfo.x, 0u, 16u), extractInt(lightingInfo.y, 16u, 16u), extractInt(lightingInfo.y, 0u, 16u)));
    } else {
        inputs.normal = normalize(vec3(extractInt(lightingInfo.x, 24u, 4u), extractInt(lightingInfo.x, 28u, 4u), extractInt(lightingInfo.y, 24u, 4u)));
    }
    inputs.color = unpackColorABGR(colorIn);
    inputs.texture = texCoordIn;
    // default value for overlay
    // this is ignored by quartz's vertex format
    inputs.whiteOverlay = true;
    inputs.overlay = 0;

    if (!QUAD) {
        inputs.lightmap = vec2((lightingInfo.x >> 24) & 0xFFu, (lightingInfo.x >> 16) & 0xFFu) * LIGHTMAP_MULTIPLIER;
    } else {
        vec2 averageingPosition = vec2((lightingInfo.y >> 28) & 0x1u, (lightingInfo.y >> 29) & 0x1u);
        vec2 vert0 = vec2((lightingInfo.x >> 00) & 0x3Fu, (lightingInfo.x >> 06) & 0x3Fu) * LIGHTMAP_MULTIPLIER;
        vec2 vert1 = vec2((lightingInfo.x >> 12) & 0x3Fu, (lightingInfo.x >> 18) & 0x3Fu) * LIGHTMAP_MULTIPLIER;
        vec2 vert2 = vec2((lightingInfo.y >> 00) & 0x3Fu, (lightingInfo.y >> 06) & 0x3Fu) * LIGHTMAP_MULTIPLIER;
        vec2 vert3 = vec2((lightingInfo.y >> 12) & 0x3Fu, (lightingInfo.y >> 18) & 0x3Fu) * LIGHTMAP_MULTIPLIER;

        // really this is supposted to be done in the fragment shader
        // but because transform feedback has no fragment shader, its done here
        vec2 vert01Avg = vert0 * averageingPosition.x + vert1 * (1 - averageingPosition.x);
        vec2 vert23Avg = vert2 * averageingPosition.x + vert3 * (1 - averageingPosition.x);
        inputs.lightmap = vert01Avg * averageingPosition.y + vert23Avg * (1 - averageingPosition.y);
    }

    VertOut outputs = vert(inputs, uniforms);

    positionOutput = outputs.position;
    // normal is packed into an xyz byte format, but then because little endian this looks backwards
    ivec3 iVertexNormal = ivec3(outputs.normal * 127);
    normalOutput |= uint(iVertexNormal.z & 0xFF);
    normalOutput <<= 8;
    normalOutput |= uint(iVertexNormal.y & 0xFF);
    normalOutput <<= 8;
    normalOutput |= uint(iVertexNormal.x & 0xFF);

    colorOutput = packColorABGR(outputs.color);
    textureOutput = outputs.texture;
    overlayOutput = uint(outputs.overlay * 15.0) | ((uint(int(bool(outputs.whiteOverlay))) * 7u + 3u) << 16);
    lightmapOutput = packLightPos(outputs.lightmap);
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
    float totalColorMultiplier = ambientOcclusion * intermediate.diffuse;

    DynamicLightingOutput outputs;
    outputs.lightmap = averaged.xy;
    outputs.colorMultiplier = vec4(totalColorMultiplier, totalColorMultiplier, totalColorMultiplier, 1);
    return outputs;
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

uint packLightPos(vec2 lightPos) {
    lightPos *= 255;
    uvec2 uLightPos = uvec2(lightPos);
    uint packedLightPos = 0u;
    packedLightPos |= (uLightPos.x & 0xFFu);
    packedLightPos |= (uLightPos.y & 0xFFu) << 16;
    return packedLightPos;
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

uniform samplerBuffer dynamicMatrices;

DynamicMatrixPair getDynamicMatrix(int matrixID) {
    DynamicMatrixPair pair;

    pair.modelMatrix[0] = texelFetch(dynamicMatrices, matrixID * 8 + 0);
    pair.modelMatrix[1] = texelFetch(dynamicMatrices, matrixID * 8 + 1);
    pair.modelMatrix[2] = texelFetch(dynamicMatrices, matrixID * 8 + 2);
    pair.modelMatrix[3] = texelFetch(dynamicMatrices, matrixID * 8 + 3);

    pair.normalMatrix[0] = texelFetch(dynamicMatrices, matrixID * 8 + 4).xyz;
    pair.normalMatrix[1] = texelFetch(dynamicMatrices, matrixID * 8 + 5).xyz;
    pair.normalMatrix[2] = texelFetch(dynamicMatrices, matrixID * 8 + 6).xyz;

    return pair;
}

uniform usamplerBuffer dynamicLights;

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

struct PackedDynamicLightInfo {
// opengl doesnt support smaller types than this, unfortunately
    uint lightingInfo[32];
};

layout(std430, binding = 1) buffer dynamicLightBuffer {
    PackedDynamicLightInfo dynamicLightsSSBO[];
};

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