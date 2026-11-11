package io.issac.pleosandroid.core.cardui.print

/** Shade the actual regional illustration and frame. The native lettering is printed above this layer. */
internal const val REGIONAL_FOIL_AGSL = """
uniform shader face;
uniform shader plate;
uniform float2 viewport;
uniform float2 plateSize;
uniform float2 rotation;
uniform float lamp;
uniform float tier;
uniform float grade;
uniform float alternate;
uniform float4 artWindow;
uniform float2 artCorners;

const float TAU = 6.2831853;

float luma(float3 c) { return dot(c,float3(.25,.58,.17)); }
float3 illuminate(float3 ink,float3 light) { return ink+(1.-ink)*clamp(light,0.,1.); }
float gauss(float x) { return exp(-x*x); }
float3 prism(float x) {
    return .56+.42*cos(TAU*(x+float3(.03,.70,.38)));
}

half4 main(float2 pixel) {
    if (pixel.x<0. || pixel.y<0. || pixel.x>=viewport.x || pixel.y>=viewport.y) return half4(0.);
    float2 uv=clamp(pixel/viewport,0.,1.);
    half4 sourceInk=face.eval(pixel);
    if (sourceInk.a<.001) return sourceInk;
    float3 base=float3(sourceInk.rgb)/float(sourceInk.a);
    float2 p=uv-.5;
    float metal=plate.eval(uv*plateSize).a;
    float2 point=uv*float2(400.,600.);
    float2 edge=max(artWindow.xy-point,point-artWindow.zw);
    float2 rounded=edge+artCorners.x;
    float curveDist=length(max(rounded,0.))+min(max(rounded.x,rounded.y),0.)-artCorners.x;
    float bevelDist=(edge.x+edge.y+artCorners.y)*.70710678;
    float aa=200./viewport.x;
    float art=(1.-smoothstep(-aa,aa,max(curveDist,bevelDist)))*(1.-metal);
    float2 normalTilt=sin(rotation*.017453293);
    float lampX=sin(lamp*.017453293);
    // A mirror moves its reflected ray through twice the angle of the surface.
    float slide=2.35*normalTilt.x+1.08*lampX-.68*normalTilt.y;
    float angle=normalTilt.x*2.+lampX;
    float cross=p.x+.48*p.y+.065*sin(p.y*5.2+p.x*1.6)-slide;
    float broad=gauss(cross/(.27+.018*tier));
    float glint=gauss((cross-.018)/(.071+.005*tier));
    float shade=gauss((cross+.23)/.16);
    float3 silver=float3(.91,.97,1.);
    float3 gold=float3(1.,.77,.40);
    float3 inkTint=mix(silver,gold,.12+.26*grade);
    float3 col=base;
    float surface=mix(1.,.76,art);

    // Uncoated stock only catches a broad satin highlight.
    float grazing=smoothstep(.012,.19,length(normalTilt));
    col*=1.-shade*(.048+.073*grazing)*surface;
    col=illuminate(col,silver*(broad*(.084+.017*tier)+glint*.12*grazing)*surface);
    if (tier<.5) return half4(half3(col)*sourceInk.a,sourceInk.a);

    // Brushed foil has a bright crest and a dark bevel, both confined by the plate's real alpha.
    float brush=.5+.5*cos(TAU*(uv.y+.075*uv.x)*min(144.,viewport.y*.27));
    float bevel=glint*(.76+.24*brush);
    col*=1.-metal*(.19+.025*tier)*shade;
    col=illuminate(col,inkTint*bevel*(.46+.065*tier)*metal);
    col+=inkTint*(glint*.085*metal+.014*(brush-.5)*metal);
    if (tier>1.5) {
        // A pearlescent film splits the grazing white strip into adjacent mint, violet and champagne.
        float film=cross+.065*sin(uv.y*6.+angle*2.);
        float rim=gauss((film-.12)/.105);
        float satin=gauss((film+.105)/.14);
        float3 nacre=prism(uv.y*.58+film*1.5+angle*.88+alternate*.14);
        float3 interference=mix(inkTint,nacre,.76);
        float coat=(.34+metal*.28)*surface;
        col*=1.-(.055+.06*metal)*satin;
        col=illuminate(col,interference*rim*coat+silver*satin*.13);
        col+=interference*rim*.047;
    }
    if (tier>2.5) {
        // Use the image's own relief to bend the reflection around waves, buildings and raised metal.
        half4 east=face.eval(clamp(pixel+float2(1.45,0.),float2(.5),viewport-.5));
        half4 south=face.eval(clamp(pixel+float2(0.,1.45),float2(.5),viewport-.5));
        float2 relief=clamp((float2(luma(float3(east.rgb)/max(float(east.a),.001)),
                                    luma(float3(south.rgb)/max(float(south.a),.001)))-luma(base))*4.,-.65,.65);
        float curve=.84*p.x+.49*p.y+.095*sin(uv.y*7.1+uv.x*3.8)+.12*relief.x;
        float freq=min(46.,viewport.x*.16);
        float flute=.5+.5*cos(TAU*freq*curve);
        float ridge=pow(flute,5.);
        float arc=gauss((curve-slide*.85+.06*sin(angle*4.))/.22);
        float3 chroma=prism(curve*1.48+angle*1.38+.61*uv.y+alternate*.14);
        float clear=mix(.64,1.,art);
        col*=1.-clear*.12*arc*(1.-flute);
        col=illuminate(col,chroma*arc*(.15+.42*ridge)*clear);
        col+=arc*ridge*(.045+.035*metal)*silver;
        if (tier>3.5) {
            // A second, warm engraved normal catches light on the side opposite the clear coat.
            float path=p.y+.29*p.x+.075*sin(6.*uv.x-2.7*uv.y);
            float cut=.5+.5*cos(TAU*path*min(57.,viewport.y*.14));
            float back=gauss((.67*p.x-.45*p.y+slide*.74-.13)/.19);
            float emboss=clamp(.21+abs(dot(relief,float2(.83,-.61)))*1.9+.59*pow(cut,6.),0.,1.);
            col*=1.-back*(.07+.19*(1.-cut))*(.65+.35*metal);
            col=illuminate(col,mix(gold,silver,art*.45)*back*emboss*(.60+.18*metal));
            col+=gold*back*emboss*(.044+.08*metal);
        }
        if (tier>4.5) {
            // Cross-engraved rosettes reveal a second spectral order under the thick top lacquer.
            float swirl=length((p+float2(.21,-.14))*float2(1.05,.71))+.078*sin(7.*uv.y+2.*uv.x);
            float ruled=.5+.5*cos(TAU*min(63.,viewport.x*.18)*swirl);
            float focus=gauss((swirl-.29+.37*angle-.07*normalTilt.y)/.105);
            float line=pow(ruled,5.);
            float3 order=prism(swirl*2.4-angle*1.65+alternate*.21);
            col*=1.-.13*focus*(1.-line);
            col=illuminate(col,order*focus*(.18+.57*line));
            float oil=gauss((cross+.29+.07*sin(uv.y*5.))/.047);
            col+=mix(silver,order,.42)*(.12*oil+.06*line*focus);
        }
    }
    // A printed nameplate takes less varnish. Keep white ink readable even as a specular crest crosses it.
    float inset=smoothstep(.049,.081,uv.x)*(1.-smoothstep(.895,.948,uv.x));
    float plaque=smoothstep(.744,.767,uv.y)*(1.-smoothstep(.842,.875,uv.y));
    float footer=smoothstep(.88,.90,uv.y)*(1.-smoothstep(.947,.977,uv.y));
    float legends=inset*max(plaque,footer*.82);
    float dark=1.-smoothstep(.25,.63,luma(base));
    col=mix(col,base,max(legends*(.64+.31*dark),art*.21*smoothstep(.62,.95,luma(base))));
    return half4(half3(clamp(col,0.,1.))*sourceInk.a,sourceInk.a);
}
"""
