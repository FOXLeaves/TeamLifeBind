#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location=0) in vec2 uiUv;
layout(location=0) out vec4 fragColor;
layout(std140) uniform LoadingFrame { vec4 Screen; vec4 State; };
float hash(vec2 p){p=fract(p*vec2(.1031,.1137));p+=dot(p,p.yx+19.19);return fract((p.x+p.y)*p.x);}
float noise(vec2 p){vec2 a=floor(p),b=fract(p);b=b*b*(3.-2.*b);return mix(mix(hash(a),hash(a+vec2(1,0)),b.x),mix(hash(a+vec2(0,1)),hash(a+1.),b.x),b.y);}
float line(float d,float w){return exp(-abs(d)/max(w,.0001));}
void main(){
    vec2 q=vec2(uiUv.x,1.-uiUv.y),p=(q-vec2(.5,.41))*vec2(Screen.x/Screen.y,1.);
    float t=Screen.w, progress=Screen.z<0.?.48+.15*sin(t*.7):clamp(Screen.z,0.,1.);
    float flow=noise(q*9.+vec2(t*.2,-t*.12))*.7+noise(q*29.-t*.1)*.3;
    float wipe=mix(-.24,1.24,State.x)-(q.y+(flow-.5)*.3);
    float covered=1.-smoothstep(-fwidth(wipe)*2.,fwidth(wipe)*2.,wipe);
    if(State.x<=.0001)covered=1.;if(State.x>=.9999)covered=0.;
    float radius=length(p),angle=atan(p.y,p.x);
    float ring=line(radius-.145,.0013)+line(radius-.165,.0006)*.6;
    float arc=step(fract(angle/6.2831853+.25),progress)*line(radius-.157,.0023);
    vec2 tilted=mat2(.707,-.707,.707,.707)*p;
    float crest=line(max(abs(tilted.x),abs(tilted.y))-.073,.0015);
    float orbit=line(radius-(.118+sin(angle*6.+t*.35)*.002),.0007)*.35;
    float water=q.y-(.83-progress*.1)+(flow-.5)*.05;
    float ripple=line(water,.0015)*.65+line(water+.012,.0007)*.25;
    float track=step(.15,q.x)*step(q.x,.85)*line(q.y-.9,.0008);
    float filled=step(q.x,.15+progress*.7)*track;
    float rim=line(min(min(q.x,1.-q.x),min(q.y,1.-q.y))-.018,.0005)*.25;
    float silver=ring+orbit+crest+arc+rim+ripple+track*.2+filled;
    vec3 base=vec3(.025,.041,.066);
    vec3 color=base+vec3(.72,.78,.86)*silver+vec3(.25,.17,.07)*(arc+filled);
    float moving=step(.001,State.x)*(1.-step(.999,State.x));
    vec2 slope=vec2(dFdx(wipe),dFdy(wipe))*Screen.xy;
    vec3 normal=normalize(vec3(-slope.x,-slope.y,2.2));
    float glint=pow(max(0.,dot(normal,normalize(vec3(.5,-.3,.85)))),14.);
    float meniscus=line(wipe+.004,.0024)+line(wipe+.012+(flow-.5)*.007,.0009)*.65;
    float lace=noise(q*vec2(210.,130.)+vec2(t*.16,0.));
    float foam=line(wipe+.022,.014)*smoothstep(.68,.86,lace)*.38;
    color+=moving*(meniscus*(vec3(.35,.46,.63)+vec3(1.4,1.35,1.22)*glint)+foam*vec3(.7,.8,1.));
    float alpha=State.y>.5?covered:clamp(silver*.6,0.,.9)*covered;
    color=State.y>.5?min(color,vec3(1.))*alpha:vec3(.82,.86,.91)*alpha;
    fragColor=vec4(color,alpha);
}
