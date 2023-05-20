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


struct DynamicMatrixPair {
    mat4 modelMatrix;
    mat3 normalMatrix;
};

DynamicMatrixPair getDynamicMatrix(int matrixID);

uint packNormal(vec3 normal);

// per vertex
layout(location = POSITION_LOCATION) in vec3 position;
layout(location = COLOR_LOCATION) in uint colorIn;
layout(location = TEX_COORD_LOCATION) in vec2 texCoordIn;
layout(location = NORMAL_LOCATION) in vec3 normalIn;

// per instance
layout(location = WORLD_POSITION_LOCATION) in ivec3 worldPosition;
layout(location = DYNAMIC_MATRIX_ID_LOCATION) in int dynamicMatrixID;
layout(location = STATIC_MATRIX_LOCATION) in mat4 staticMatrix;
layout(location = STATIC_NORMAL_MATRIX_LOCATION) in mat3 staticNormalMatrix;

layout(std140, binding = 0) uniform MainUBO {
    ivec4 playerBlock;
    vec4 playerSubBlock;
    ivec4 lightCunkLookupOffset;
};


layout(binding = 1) uniform usampler3D intermediateLightChunkIndexLookup;
layout(binding = 2) uniform usampler2DArray intermediateLightDataTexture[6];

const vec3 lightDirections[6] = vec3[6](vec3(1, 0, 0), vec3(0, 1, 0), vec3(0, 0, 1), vec3(-1, 0, 0), vec3(0, -1, 0), vec3(0, 0, -1));

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

    // TODO: potentially compute shader or additional feedback stage this, its the same for every vertex within an instance
    //       and the input locations are alreayd being used for the static matrices
    DynamicMatrixPair dynamicMatrices = getDynamicMatrix(dynamicMatrixID);
    mat4 modelMatrix = /*dynamicMatrices.modelMatrix */ staticMatrix;
    mat3 modelNormalMatrix = /*dynamicMatrices.normalMatrix */ mat3(staticNormalMatrix);

    positionOutput = (modelMatrix * vec4(position, 1)).xyz + worldTransform;
    normalOutput = packNormal(modelNormalMatrix * normalIn);
    colorOutput = -1;
    textureOutput = vec2(position.xy);
    overlayOutput = 0;
    lightmapOutput = 0;
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
    ivec3 iVertexNormal = ivec3(normal * 127);
    uint packedNormal = 0;
    packedNormal |= uint(iVertexNormal.z & 0xFF);
    packedNormal <<= 8;
    packedNormal |= uint(iVertexNormal.y & 0xFF);
    packedNormal <<= 8;
    packedNormal |= uint(iVertexNormal.x & 0xFF);
    return packedNormal;
}