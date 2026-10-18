uniform sampler2D texture;
varying vec2 leTexcoord;
varying vec4 leColor;
void main() {
    vec4 texel = texture2D(texture, leTexcoord) * leColor;
    if (texel.a < 0.10) discard;
    gl_FragData[0] = texel;
}
