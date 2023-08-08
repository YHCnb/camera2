precision mediump float;

varying vec2 aCoord;

uniform sampler2D vTexture;
uniform float exposure;

void main() {
    lowp vec4 textureColor = texture2D(vTexture, aCoord);
    gl_FragColor = vec4(textureColor.rgb * pow(2.0, exposure), textureColor.w);// rgb * 2^效果值
}