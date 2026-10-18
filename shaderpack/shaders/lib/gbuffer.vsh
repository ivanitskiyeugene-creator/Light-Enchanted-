
attribute vec4 mc_Entity;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform vec3 shadowLightPosition;

varying vec2 leTexcoord;
varying vec2 leLightcoord;
varying vec4 leColor;
varying vec3 leNormal;
varying vec3 leViewPos;
varying vec3 leShadowCoord;
varying float leNdotL;
varying float leMaterial;

void main() {
    leTexcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    leLightcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    leColor = gl_Color;
    leMaterial = mc_Entity.x;

    vec4 viewPos4 = gl_ModelViewMatrix * gl_Vertex;
    leViewPos = viewPos4.xyz;
    leNormal = normalize(gl_NormalMatrix * gl_Normal);
    leNdotL = max(dot(leNormal, normalize(shadowLightPosition)), 0.0);

    vec4 playerPos = gbufferModelViewInverse * viewPos4;
    vec4 shadowPos = shadowProjection * (shadowModelView * playerPos);
    leShadowCoord = shadowPos.xyz / max(shadowPos.w, 0.00001) * 0.5 + 0.5;
    gl_Position = gl_ProjectionMatrix * viewPos4;
}
