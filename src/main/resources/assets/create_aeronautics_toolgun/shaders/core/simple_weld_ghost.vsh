#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 localPosition;
out vec3 viewPosition;
out vec3 viewNormal;

void main() {
    vec3 pos = Position + ChunkOffset;
    vec4 viewPos = ModelViewMat * vec4(pos, 1.0);
    gl_Position = ProjMat * viewPos;

    mat3 normalMatrix = transpose(inverse(mat3(ModelViewMat)));
    vec3 transformedNormal = normalMatrix * Normal;
    viewNormal = length(transformedNormal) > 0.0001 ? normalize(transformedNormal) : vec3(0.0, 1.0, 0.0);
    viewPosition = viewPos.xyz;
    localPosition = Position;
    vertexDistance = fog_distance(viewPos.xyz, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
