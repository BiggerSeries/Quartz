#version 330 core

layout(location = 0) in mat4 staticModelMatrix;
layout(location = 4) in mat3 staticNormalMatrix;
layout(location = 7) in ivec3 worldPosition;
layout(location = 8) in int dynamicMatrixID;

flat out mat4 modelMatrixOut;
flat out mat3x4 normalMatrixOut;
flat out ivec3 worldPositionOut;
flat out int dynamicMatrixIDOut;

uniform samplerBuffer dynamicMatrices;

struct DynamicMatrixPair {
    mat4 modelMatrix;
    mat3 normalMatrix;
};

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

void main() {
    worldPositionOut = worldPosition;
    dynamicMatrixIDOut = dynamicMatrixID;

    DynamicMatrixPair matrices = getDynamicMatrix(dynamicMatrixID);
    modelMatrixOut = matrices.modelMatrix * staticModelMatrix;
    normalMatrixOut = mat3x4(matrices.normalMatrix * staticNormalMatrix);
}