#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GhostTime;
uniform vec3 ScanAxis;
uniform float ScanOffset;
uniform float ScanWidth;
uniform vec3 EdgeColor;
uniform float EffectStrength;
uniform float BaseAlpha;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 localPosition;
in vec3 viewPosition;
in vec3 viewNormal;

out vec4 fragColor;

void main() {
    vec4 sampled = texture(Sampler0, texCoord0);
    if (sampled.a < 0.01) {
        discard;
    }

    vec4 color = sampled * vertexColor * ColorModulator;
    vec3 viewDirection = normalize(-viewPosition);
    float fresnel = pow(clamp(1.0 - abs(dot(normalize(viewNormal), viewDirection)), 0.0, 1.0), 2.2);

    float scanCoordinate = dot(localPosition, normalize(ScanAxis));
    float scanDistance = abs(scanCoordinate - ScanOffset);
    float scan = 1.0 - smoothstep(0.0, max(ScanWidth, 0.001), scanDistance);
    float pulse = 0.94 + 0.06 * sin(GhostTime * 4.2);
    float edgeEnergy = fresnel * EffectStrength;
    float scanEnergy = scan * EffectStrength;

    color.rgb *= pulse;
    color.rgb = mix(color.rgb, EdgeColor, clamp(edgeEnergy * 0.72 + scanEnergy * 0.82, 0.0, 0.92));
    color.rgb += EdgeColor * (edgeEnergy * 0.28 + scanEnergy * 0.38);
    color.a = clamp(max(color.a * BaseAlpha, edgeEnergy * 0.34 + scanEnergy * 0.50), 0.0, 0.78);

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
