#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform SigilFrame { vec4 ViewportTime; vec4 AccentSeed; };
layout(location=0) out vec4 fragColor;
mat2 turn(float a){float c=cos(a),s=sin(a);return mat2(c,-s,s,c);}
float torus(vec3 p,float r,float w){return length(vec2(length(p.xz)-r,p.y))-w;}
float scene(vec3 p){
    float t=ViewportTime.z,seed=AccentSeed.w;
    p.xz=turn(t*.38+seed)*p.xz;p.yz=turn(.38+sin(t*.23)*.25)*p.yz;
    float d=length(p)-.32;
    d=min(d,torus(p,.68,.018));
    vec3 q=p;q.xy=turn(1.15)*q.xy;d=min(d,torus(q,.72,.014));
    q=p;q.yz=turn(-1.05)*q.yz;d=min(d,torus(q,.77,.012));
    if(ViewportTime.w>=5.){q=p;q.xy=turn(t*.24)*q.xy;d=min(d,torus(q,.88,.009));}
    return d;
}
vec3 normalAt(vec3 p){vec2 e=vec2(.0015,0);return normalize(vec3(scene(p+e.xyy)-scene(p-e.xyy),scene(p+e.yxy)-scene(p-e.yxy),scene(p+e.yyx)-scene(p-e.yyx)));}
void main(){
    vec2 uv=(gl_FragCoord.xy*2.-ViewportTime.xy)/min(ViewportTime.x,ViewportTime.y);
    vec3 origin=vec3(0,.20,2.9),ray=normalize(vec3(uv*1.02,-2.65));
    vec3 color=vec3(0),accent=AccentSeed.rgb;float travel=0.,haze=0.,alpha=0.;
    for(int i=0;i<64;i++){
        vec3 p=origin+ray*travel;float d=scene(p);
        haze+=exp(-abs(d)*58.)*.010;
        if(d<.0018){
            vec3 n=normalAt(p),light=normalize(vec3(-.6,.9,1.2));
            float diffuse=max(0.,dot(n,light)),rim=pow(1.-max(0.,dot(n,-ray)),2.3);
            float rune=.5+.5*sin(atan(p.z,p.x)*18.+p.y*28.-ViewportTime.z*1.7);
            vec3 gold=vec3(1.,.81,.44);
            color=mix(accent,gold,smoothstep(.65,.95,rune)*.45)*(.28+diffuse*.8)+accent*rim*2.;
            if(length(p)<.38){float energy=pow(max(0.,sin(p.x*28.+ViewportTime.z)*sin(p.y*22.-ViewportTime.z*.7)),3.);color=accent*(.45+energy*2.6)+gold*rim*.5;}
            alpha=.96;break;
        }
        travel+=max(.003,d*.82);if(travel>5.)break;
    }
    float halo=exp(-dot(uv,uv)*3.3)*.055;
    color+=accent*(haze*1.2+halo);alpha=max(alpha,clamp(haze*.9+halo,0.,.7));
    float edge=1.-smoothstep(.94,1.08,length(uv));alpha*=edge;
    color=vec3(1.)-exp(-color*1.3);
    fragColor=vec4(color*alpha,alpha);
}
