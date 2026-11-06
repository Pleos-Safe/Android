package io.issac.pleosandroid.core.designsystem.component

/** The approved 360 × 520 foil material, evaluated in print coordinates at every display size. */
internal const val FOIL_MATERIAL_AGSL = """
uniform shader uBase;
uniform shader uSpot;
uniform float2 uSize;
uniform float uAngle;
uniform float uGrade;
uniform float uStrength;
uniform float4 uArt;
uniform float4 uArtCorners;
uniform float4 uBezel;
uniform float4 uCrest;
uniform float uRuleY;

// The reference mask was rendered at 594 × 858. Relief sampling stays constant in print coordinates.
const float2 uTexel = float2(1.0 / 594.0, 1.0 / 858.0);

float4 baseAt(float2 uv) {
    float2 point = clamp(uv, 0.5 / uSize, 1.0 - 0.5 / uSize) * uSize;
    float4 color = uBase.eval(point);
    return float4(color.rgb / max(color.a, 0.0001), color.a);
}

float artCoverage(float2 p) {
    float2 center = (uArt.xy + uArt.zw) * 0.5;
    float2 halfSize = (uArt.zw - uArt.xy) * 0.5;
    float2 quadrant = p - center;
    float radius = quadrant.y < 0.0
        ? (quadrant.x < 0.0 ? uArtCorners.x : uArtCorners.y)
        : (quadrant.x < 0.0 ? uArtCorners.w : uArtCorners.z);
    float2 distance = abs(quadrant) - halfSize + radius;
    float edge = min(max(distance.x, distance.y), 0.0) + length(max(distance, 0.0)) - radius;
    return 1.0 - smoothstep(-0.35, 0.35, edge);
}

float3 maskAt(float2 uv) {
    float2 p = uv * float2(360.0, 520.0);
    float art = artCoverage(p);
    bool outer = p.x < 10.0 || p.x > 350.0 || p.y < 10.0 || p.y > 510.0;
    bool bezel = p.x > uBezel.x - 3.0 && p.x < uBezel.z + 3.0
        && p.y > uBezel.y - 3.0 && p.y < uBezel.w + 3.0
        && (p.x < uBezel.x + 3.0 || p.x > uBezel.z - 3.0
            || p.y < uBezel.y + 3.0 || p.y > uBezel.w - 3.0);
    bool crest = p.x > uCrest.x && p.x < uCrest.z && p.y > uCrest.y && p.y < uCrest.w;
    bool ornament = p.x > 230.0 && p.y > 399.0 && p.y < 477.0;
    bool rule = abs(p.y - uRuleY) < 1.2;
    float metal = 0.0;
    if (outer || bezel || crest || ornament || rule) {
        float lum = dot(baseAt(uv).rgb, float3(0.25, 0.60, 0.15));
        metal = clamp((lum - 0.24) / 0.55, 0.0, 1.0);
    }
    return float3(uSpot.eval(p).a * art, art, metal);
}

const float TAU=6.283185307;
float3 screen(float3 a,float3 b){return 1.-(1.-a)*(1.-clamp(b,0.,1.));}
float3 dodge(float3 a,float3 b){return min(float3(1.),a/max(float3(.05),1.-b));}
float hash(float2 p){return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453);}
float3 spectrum(float h){return .5+.5*cos(TAU*(h+float3(0.,-.333333,.333333)));}
float luma(float3 c){return dot(c,float3(.24,.61,.15));}
float printHeight(float2 p){float3 m=maskAt(p);return luma(baseAt(p).rgb)*.28+m.r*.46+m.b*.26;}
float2 printSlope(float2 uv){
  float2 d=uTexel*1.5;
  return float2(printHeight(uv+float2(d.x,0.))-printHeight(uv-float2(d.x,0.)),printHeight(uv+float2(0.,d.y))-printHeight(uv-float2(0.,d.y)));
}
half4 main(float2 pixel){
  float2 uv = pixel / uSize;
  if (uBase.eval(pixel).a < 0.0001) return half4(0.0);
  float4 base=baseAt(uv);float3 mask=maskAt(uv);
  float ink=mask.r,art=mask.g,metal=mask.b,a=uAngle/24.;
  float3 col=base.rgb;
  float3 light=normalize(float3(.75*sin(a*1.65+.5),-.55+.20*cos(a*1.3),1.));
  float3 halfVector=normalize(light+float3(0.,0.,1.));
  float2 relief=printSlope(uv);
  float grain=hash(floor(uv*float2(510.,738.)));
  float satin=sin(uv.y*1900.+sin(uv.x*27.)*.8);
  float lightRoll=.5+.5*cos(uv.x*4.4-uv.y*3.5-a*3.1);
  float stamped=clamp((luma(base.rgb)-.39)/.32,0.,1.)*(1.-art)*(1.-metal);
  if(uGrade<3.5){
    // A fine brushed silver plate is printed into the existing artwork, on matte paper.
    float3 normal=normalize(float3(relief*2.2+float2((grain-.5)*.12,satin*.12),1.));
    float flash=pow(max(0.,dot(normal,halfVector)),35.);
    float polish=.15+.85*lightRoll;
    float coverage=clamp(ink*.86+metal,0.,1.);
    col*=1.-coverage*(.15-.08*flash)*uStrength;
    float3 silver=mix(float3(.65,.78,.87),float3(.95,.98,1.),flash);
    col=screen(col,silver*coverage*(.065+.35*flash)*polish*uStrength);
    col=mix(col,base.rgb*(.64+.66*lightRoll)+silver*.12*flash,metal*.75*uStrength);
  }else if(uGrade<4.5){
    // Continuous etched foil grooves. Their normals, not floating particles, catch the light.
    float curve=uv.x*.77+uv.y*.36+.115*sin(uv.y*5.4+uv.x*1.2)+.027*sin(uv.x*14.-uv.y*7.);
    float2 tangent=float2(.77+.138*cos(uv.y*5.4+uv.x*1.2)+.378*cos(uv.x*14.-uv.y*7.),.36+.621*cos(uv.y*5.4+uv.x*1.2)-.189*cos(uv.x*14.-uv.y*7.));
    float turn=curve*TAU*68.;
    float ridge=.5+.5*cos(turn);
    float slope=sin(turn);
    float3 normal=normalize(float3(tangent*slope*.42+relief*.34,1.));
    float reflection=pow(max(0.,dot(normal,halfVector)),23.);
    float shadow=max(0.,-dot(normal.xy,light.xy));
    float lightCoverage=.28+.72*pow(lightRoll,1.6);
    float substrate=mix(.82,.54,art);
    float fine=(.72+.28*grain);
    col*=1.-(.06+.17*shadow)*substrate*uStrength;
    col=screen(col,float3(.85,.93,.99)*reflection*lightCoverage*substrate*.30*fine*uStrength);
    float3 interference=spectrum(curve*.84+a*.47+ridge*.05);
    col=screen(col,interference*reflection*lightCoverage*mix(.38,.27,art)*uStrength);
    col=mix(col,dodge(col,interference*reflection*lightCoverage*.40),.6*uStrength);
    col+=float3((ridge-.5)*.019*lightCoverage)*(1.-stamped)*uStrength;
    col=screen(col,float3(.86,.96,1.)*ink*.14*reflection*uStrength);
    float3 chrome=base.rgb*(.73+.57*lightRoll)+mix(float3(.85,.95,1.),interference,.23)*reflection*.24;
    col=mix(col,chrome,metal*.8*uStrength);
  }else{
    // Two engraved depth layers: long curved contours and the relief already in the art.
    float x=uv.x,y=uv.y*1.44444;
    float f=y*1.0+x*.35+.06*sin(x*4.6+y*1.7)+.045*sin(y*3.8-x*1.5);
    float2 tangent=float2(.35+.276*cos(x*4.6+y*1.7)-.0675*cos(y*3.8-x*1.5),1.+.102*cos(x*4.6+y*1.7)+.171*cos(y*3.8-x*1.5));
    float phase=f*TAU*84.;
    float ridge=.5+.5*cos(phase);
    float cut=sin(phase)*(1.1+.30*cos(phase));
    float3 normal=normalize(float3(tangent*cut*.49+relief*2.65,1.));
    float3 underNormal=normalize(float3(tangent*cos(phase*.53+.6)*.21-relief*.5,1.));
    float mirror=pow(max(0.,dot(normal,halfVector)),31.);
    float under=pow(max(0.,dot(underNormal,normalize(float3(-light.x,light.y*.7,1.)))),12.);
    float shadow=clamp(-dot(normal.xy,light.xy),0.,1.);
    float raised=clamp(length(relief)*5.,0.,1.);
    float cover=mix(.90,.58,art);
    float facing=.38+.62*(.5+.5*sin(uv.x*3.2+uv.y*4.4-a*2.8));
    float3 warmWhite=float3(1.,.95,.80),gold=float3(1.,.72,.28);
    float3 iridescence=spectrum(f*.92+a*.51+.04*ridge);
    col*=1.-cover*(.095+.24*shadow)*uStrength;
    float3 reflected=mix(gold*.72+warmWhite*.28,float3(.93,.97,1.),art*.9);
    col=screen(col,reflected*mirror*facing*cover*.56*(.83+.17*grain)*uStrength);
    col=screen(col,iridescence*pow(under,1.5)*(.4+.6*mirror)*mix(.28,.19,art)*uStrength);
    col=mix(col,dodge(col,iridescence*mirror*(.2+.25*under)),.68*uStrength);
    col=screen(col,reflected*raised*ink*(.05+.28*mirror)*uStrength);
    col+=float3((ridge-.5)*.045*facing)*cover*uStrength;
    float3 solidGold=base.rgb*(.54+.71*lightRoll)+gold*(.045+.09*lightRoll)+warmWhite*mirror*.35;
    col=mix(col,solidGold,metal*.91*uStrength);
    col=screen(col,gold*ink*raised*mirror*.12*uStrength);
  }
  // Preserve the identity of the picture while letting the etched foil carry colour.
  float3 difference=col-base.rgb;
  float neutral=(difference.r+difference.g+difference.b)/3.;
  difference=mix(difference,float3(neutral)+(difference-float3(neutral))*.40,art*.56);
  col=base.rgb+difference;
  col=mix(col,base.rgb,stamped*.94);
  return half4(clamp(col,0.,1.) * base.a,base.a);
}
"""
