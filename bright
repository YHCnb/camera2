precision mediump float;

varying vec2 aCoord;

uniform sampler2D vTexture;
uniform float brightness;

void main() {
    lowp vec4 textureColor = texture2D(vTexture, aCoord);
    gl_FragColor = vec4((textureColor.rgb + vec3(brightness)), textureColor.w);
}
