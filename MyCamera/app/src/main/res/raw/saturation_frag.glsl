precision mediump float;

varying vec2 aCoord;

uniform sampler2D vTexture;
uniform float saturation;

void main() {
    vec4 textureColor = texture2D(vTexture, aCoord);
    float averageColor = (textureColor.r + textureColor.g + textureColor.b) / 3.0;
    gl_FragColor = vec4(mix(vec3(averageColor), textureColor.rgb, saturation), textureColor.a);
}
