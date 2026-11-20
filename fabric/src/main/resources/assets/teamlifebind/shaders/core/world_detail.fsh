#version 330
#extension GL_ARB_separate_shader_objects : require

// Native-resolution analytic engraving. The soft body remains in the bounded
// HDR volume target; thin lines use derivative antialiasing at display pixels.
layout(std140) uniform MagicFrame {
    mat4 InvViewProjection;
    vec4 ViewportTime; vec4 CenterRadius; vec4 AxisExtent; vec4 TintTier;
    vec4 Animation; vec4 Profile; vec4 Dynamics; vec4 Identity;
};
uniform sampler2D SceneDepth;
layout(location=0) in vec2 screenUv;
layout(location=0) out vec4 fragColor;
const float TAU=6.28318530718;
float ink(float distance, float width) {
    float aa=max(fwidth(distance),0.00035);
    return 1.0-smoothstep(width-aa,width+aa,abs(distance));
}
vec2 segment(vec3 ro, vec3 rd, vec3 a, vec3 b) {
    vec3 span=b-a,w=ro-a;
    float aa=dot(rd,rd),bb=dot(rd,span),cc=max(dot(span,span),1e-8);
    float dd=dot(rd,w),ee=dot(span,w),denom=aa*cc-bb*bb;
    float hit=denom>1e-8?(bb*ee-cc*dd)/denom:-dd/max(aa,1e-8);
    float u=clamp((ee+bb*hit)/cc,0.0,1.0);
    hit=dot(a+span*u-ro,rd)/max(aa,1e-8);
    return vec2(length(ro+rd*hit-a-span*u),hit);
}
mat2 turn(float a) { float c=cos(a),s=sin(a);return mat2(c,-s,s,c); }
float stroke(vec2 p,vec2 a,vec2 b,float width) {
    vec2 d=b-a;return ink(length(p-a-d*clamp(dot(p-a,d)/max(dot(d,d),1e-6),0.0,1.0)),width);
}
// Fourteen independently constructed inscriptions. These are geometry, not palette swaps.
float schoolMark(vec2 p,int school,float time,float seed) {
    float r=length(p),a=atan(p.y,p.x),line=0.0;
    if(r>1.08)return 0.0;
    if(school==0){ // fire: a breathing crown with curled flame tips
        float flame=0.52+0.17*cos(a*5.0+0.45*sin(a*3.0-time*2.0));
        line=ink(r-flame,0.010)+ink(r-flame*0.62,0.006)*0.6;
        line+=ink(sin(a*5.0-r*4.0+time),0.022)*smoothstep(0.6,0.7,r)*(1.0-smoothstep(0.85,1.0,r));
    }else if(school==1){ // water: displaced ripple centres and intersecting current
        for(int i=0;i<3;i++){
            vec2 center=vec2(sin(time+float(i)*2.1),cos(time*.7+float(i)*2.1))*.14;
            line+=ink(length(p-center)-(0.32+float(i)*.22),.007);
        }
        line+=ink(p.y-.11*sin(p.x*10.0-time*2.0),.006)*step(abs(p.x),.7)*.6;
    }else if(school==2){ // ice: six branching crystal arms, with a faceted hub
        float cell=mod(a+TAU/12.0,TAU/6.0)-TAU/12.0;
        vec2 q=vec2(cos(cell),sin(cell))*r;
        line=stroke(q,vec2(.12,0),vec2(.88,0),.009);
        line+=stroke(vec2(q.x,abs(q.y)),vec2(.32,0),vec2(.50,.18),.007);
        line+=stroke(vec2(q.x,abs(q.y)),vec2(.58,0),vec2(.76,.16),.006);
        line+=ink(max(abs(p.x)*.866+abs(p.y)*.5,abs(p.y))-.17,.008);
    }else if(school==3){ // wind: two open logarithmic spirals
        line=ink(sin(a*2.0-log(max(r,.04))*5.0+time*2.4),.025)*smoothstep(.1,.25,r)*(1.0-smoothstep(.72,.95,r));
        line+=ink(r-.85,.004)*smoothstep(-.4,.1,cos(a*3.0-time));
    }else if(school==4){ // earth: broken angular plates and crooked radial faults
        float cell=mod(a+TAU/16.0,TAU/8.0)-TAU/16.0;
        float edge=r*cos(cell);
        line=ink(edge-.62,.012)+ink(edge-.35,.008)*.7;
        line+=ink(cell-.06*sin(r*19.0+floor(a*8.0/TAU)*2.0),.015)*smoothstep(.2,.3,r)*step(r,.88);
    }else if(school==5){ // lightning: six connected zigzags with forked ends
        float cell=mod(a+TAU/12.0,TAU/6.0)-TAU/12.0;
        vec2 q=vec2(cos(cell),sin(cell))*r;
        float bolt=.065*sin(q.x*27.0+floor(time*8.0))+.025*sin(q.x*63.0);
        line=ink(q.y-bolt,.012)*smoothstep(.12,.2,q.x)*step(q.x,.88);
        line+=stroke(vec2(q.x,abs(q.y)),vec2(.53,.03),vec2(.75,.24),.008);
    }else if(school==6){ // light: four long rays and a rotated diamond halo
        vec2 q=turn(.785398)*p;
        line=ink(abs(q.x)+abs(q.y)-.58,.009)+ink(r-.83,.006);
        line+=max(ink(p.x,.008)*step(abs(p.y),.95),ink(p.y,.008)*step(abs(p.x),.95));
        line+=ink(abs(q.x)+abs(q.y)-.22,.011);
    }else if(school==7){ // shadow: opposed crescents with deliberately empty centres
        vec2 q=turn(time*.25)*p;
        float outer=length(q)-.68,cut=length(q-vec2(.24,.08))-.62;
        line=ink(max(outer,-cut),.008)+ink(length(q+vec2(.28,0))-.25,.005)*step(q.x,-.1);
    }else if(school==8){ // arcane: counter-rotating triangles and vertex diamonds
        for(int i=0;i<6;i++){
            float t=float(i)*TAU/6.0+time*.2;
            vec2 v=vec2(cos(t),sin(t))*.72,w=vec2(cos(t+TAU/3.0),sin(t+TAU/3.0))*.72;
            line+=stroke(p,v,w,.005)*.6;
            line+=ink(abs(p.x-v.x)+abs(p.y-v.y)-.055,.006);
        }
    }else if(school==9){ // nature: five veined leaves
        float cell=mod(a+TAU/10.0,TAU/5.0)-TAU/10.0;
        vec2 q=vec2(cos(cell),sin(cell))*r;
        float leaf=.18*sin(clamp((q.x-.16)/.64,0.0,1.0)*3.14159);
        line=ink(abs(q.y)-leaf,.007)*step(.16,q.x)*step(q.x,.80);
        line+=stroke(q,vec2(.15,0),vec2(.79,0),.005);
        line+=ink(abs(q.y)-.16*fract(q.x*8.0-time*.25),.005)*step(abs(q.y),leaf)*.55;
    }else if(school==10){ // gravity: precessing eccentric orbits around a singularity
        for(int i=0;i<3;i++){
            vec2 q=turn(float(i)*TAU/3.0+time*.25)*p;
            line+=ink(length(q*vec2(1.0,2.7))-.76,.009);
        }
        line+=ink(r-.13,.015);
    }else if(school==11){ // time: a real dial, minute marks and two moving hands
        line=ink(r-.79,.006)+ink(r-.69,.004);
        line+=ink(sin(a*30.0),.025)*step(.73,r)*step(r,.78)*.65;
        line+=ink(sin(a*6.0),.018)*step(.62,r)*step(r,.78);
        line+=stroke(p,vec2(0),vec2(cos(time*.7),sin(time*.7))*.58,.008);
        line+=stroke(p,vec2(0),vec2(cos(-time*1.8),sin(-time*1.8))*.43,.010);
    }else if(school==12){ // blood: a pulsing heart sigil with inward flowing vessels
        vec2 q=p/(.78+.035*sin(time*9.0));q.y+=.10;
        float f=dot(q,q)-.42;
        line=ink(f*f*f-q.x*q.x*q.y*q.y*q.y,.003);
        line+=ink(sin(a*3.0+r*8.0+time*3.0),.022)*smoothstep(.72,.78,r)*step(r,.96)*.6;
    }else{ // spirit: three interlaced, drifting teardrops
        for(int i=0;i<3;i++){
            vec2 q=turn(float(i)*TAU/3.0+time*.22)*p;
            line+=ink(length(q-vec2(.22,0))-(.32+.10*cos(atan(q.y,q.x)*2.0)),.006);
        }
        line+=ink(r-.10,.012);
    }
    return min(line,2.4);
}
float wire(vec3 ro,vec3 rd,vec3 a,vec3 b,float width,float limit) {
    vec2 hit=segment(ro,rd,a,b);
    return hit.y>.015&&hit.y<limit?ink(hit.x,width):0.0;
}
float halo(vec3 ro,vec3 rd,vec3 center,vec3 normal,float radius,float width,float limit) {
    float denom=dot(rd,normal);
    if(abs(denom)<1e-7)return 0.0;
    float t=dot(center-ro,normal)/denom;
    return t>.015&&t<limit?ink(length(ro+rd*t-center)-radius,width):0.0;
}
// Intersect both sides so a player inside a dome still sees the far wall.
float shell(vec3 ro,vec3 rd,float radius,int mode,float time,float limit) {
    float aa=dot(rd,rd),b=dot(ro,rd),discriminant=b*b-aa*(dot(ro,ro)-radius*radius);
    if(discriminant<0.0)return 0.0;
    float light=0.0;
    for(int side=0;side<2;side++){
        float t=(-b+(side==0?-1.0:1.0)*sqrt(discriminant))/aa;
        if(t<=.015||t>=limit)continue;
        vec3 p=(ro+rd*t)/radius;
        float latitude=asin(clamp(p.y,-1.0,1.0)),longitude=atan(p.z,p.x);
        if(mode==0){
            float front=latitude-sin(time*2.0)*.65;
            light+=ink(front,.012)*.8+ink(sin(longitude*6.0+latitude*3.0-time),.018)*.22;
        }else if(mode==1){
            float cell=min(abs(sin(longitude*6.0+latitude*5.0)),abs(sin(longitude*6.0-latitude*5.0)));
            light+=ink(cell,.012)*(.38+.2*sin(latitude*12.0-time*3.0));
        }else if(p.y>=-.02){
            light+=ink(sin(longitude*6.0),.012)*.45+ink(sin(latitude*9.0-time*.35),.018)*.28;
        }
    }
    return light;
}
float deliveryDetail(vec3 ro,vec3 rd,vec3 axis,float halfSpan,int mechanic,float age,float life,float seed,float limit) {
    vec3 helper=abs(axis.y)>.95?vec3(1,0,0):vec3(0,1,0);
    vec3 u=normalize(cross(helper,axis)),v=cross(axis,u);
    vec3 o=vec3(dot(ro,u),dot(ro,v),dot(ro,axis)),d=vec3(dot(rd,u),dot(rd,v),dot(rd,axis));
    float time=age+mod(seed*.73,6.0),h=max(halfSpan,.08),light=0.0;
    if(mechanic==0){ // bolt: pointed sheath following the actual finite flight segment
        float tip=h*.96,tail=-h*.8;
        for(int i=0;i<4;i++){
            float a=float(i)*TAU/4.0+time*2.0;
            vec3 shoulder=vec3(cos(a)*.16,sin(a)*.16,mix(tail,tip,.65));
            light+=wire(o,d,vec3(0,0,tail),shoulder,.008,limit)*.7;
            light+=wire(o,d,shoulder,vec3(0,0,tip),.011,limit);
        }
    }else if(mechanic==1){ // beam: three accelerator rings with a moving pulse
        for(int i=0;i<3;i++){
            float t=fract(age*1.2+float(i)/3.0);
            light+=halo(o,d,vec3(0,0,mix(-h*.9,h*.9,t)),vec3(0,0,1),.23,.009,limit)*(.5+.5*sin(t*3.14159));
        }
    }else if(mechanic==2){ // cone: connected flared ribs and an expanding mouth
        for(int i=0;i<8;i++){
            float a=float(i)*TAU/8.0;
            vec3 tip=vec3(cos(a)*.82,sin(a)*.82,h*.86);
            light+=wire(o,d,vec3(0,0,-h*.9),tip,.006,limit)*.55;
        }
        light+=halo(o,d,vec3(0,0,h*.86),vec3(0,0,1),.82,.008,limit);
    }else if(mechanic==3){ // nova: spherical shock front, distinct from a static shield
        light+=shell(ro,rd,.18+sqrt(life)*.84,0,time,limit);
    }else if(mechanic==4){ // orbit: inclined moving rings with connected satellite diamonds
        for(int i=0;i<3;i++){
            float a=time*.65+float(i)*TAU/3.0;
            vec3 normal=normalize(vec3(cos(a),.7,sin(a)));
            light+=halo(ro,rd,vec3(0),normal,.72,.007,limit)*.6;
            vec3 center=vec3(cos(a)*.7,sin(a*1.3)*.25,sin(a)*.7);
            light+=wire(ro,rd,center-vec3(.08,0,0),center+vec3(0,.12,0),.008,limit);
            light+=wire(ro,rd,center+vec3(0,.12,0),center+vec3(.08,0,0),.008,limit);
        }
    }else if(mechanic==5){ // chain: eight joined, time-coherent lightning links
        for(int i=0;i<8;i++){
            float t=float(i)/8.0,n=float(i+1)/8.0;
            vec3 a=vec3(sin(t*37.0+time*4.0)*sin(t*3.14159)*.24,cos(t*29.0+time*3.0)*sin(t*3.14159)*.18,mix(-h,h,t));
            vec3 b=vec3(sin(n*37.0+time*4.0)*sin(n*3.14159)*.24,cos(n*29.0+time*3.0)*sin(n*3.14159)*.18,mix(-h,h,n));
            light+=wire(o,d,a,b,.011,limit);
        }
    }else if(mechanic==6){ // wave: two raised crescent fronts
        vec3 forward=length(axis.xz)>.001?normalize(vec3(axis.x,0,axis.z)):vec3(0,0,1);
        vec3 side=cross(vec3(0,1,0),forward);
        for(int i=0;i<8;i++){
            float a=-1.2+float(i)*.3,b=a+.3;
            vec3 p=side*sin(a)*.85+forward*cos(a)*.7+vec3(0,.12+cos(a*2.0+age*4.0)*.06,0);
            vec3 q=side*sin(b)*.85+forward*cos(b)*.7+vec3(0,.12+cos(b*2.0+age*4.0)*.06,0);
            light+=wire(ro,rd,p,q,.011,limit);
            light+=wire(ro,rd,p-forward*.18-vec3(0,.07,0),q-forward*.18-vec3(0,.07,0),.006,limit)*.55;
        }
    }else if(mechanic==7){ // vortex: a continuous rising corkscrew
        for(int i=0;i<8;i++){
            float t=float(i)/8.0,n=float(i+1)/8.0,a=t*TAU*1.5-age*2.6,b=n*TAU*1.5-age*2.6;
            vec3 p=vec3(cos(a)*(.2+t*.6),t*1.2-.25,sin(a)*(.2+t*.6));
            vec3 q=vec3(cos(b)*(.2+n*.6),n*1.2-.25,sin(b)*(.2+n*.6));
            light+=wire(ro,rd,p,q,.012,limit);
        }
    }else if(mechanic==8){ // meteor: triangular heat fins and a falling core
        for(int i=0;i<5;i++){
            float a=float(i)*TAU/5.0+time;
            vec3 rim=vec3(cos(a)*.3,sin(a)*.3,.1);
            light+=wire(o,d,vec3(0,0,.6),rim,.014,limit);
            light+=wire(o,d,rim,vec3(cos(a)*.16,sin(a)*.16,-.95),.006,limit)*.65;
        }
    }else if(mechanic==9){ // mine: low armed tripwire teeth, without a flying trail
        light+=halo(ro,rd,vec3(0,.06,0),vec3(0,1,0),.62,.008,limit);
        for(int i=0;i<6;i++){
            float a=float(i)*TAU/6.0;vec3 side=vec3(cos(a),0,sin(a));
            light+=wire(ro,rd,side*.50,side*.66+vec3(0,.18+.04*sin(age*5.0),0),.009,limit);
        }
    }else if(mechanic==10){ // dash: a swept-back, crossed wake
        for(int i=0;i<4;i++){
            float a=float(i)*TAU/4.0+.785398;
            light+=wire(o,d,vec3(cos(a)*.55,sin(a)*.55,-h*.85),vec3(0,0,h*.85),.009,limit);
        }
    }else if(mechanic==11){ // blink: opposed elliptical gates
        for(int i=0;i<2;i++){
            vec3 q=o-vec3(0,0,float(i)*.44-.22);
            light+=halo(q*vec3(1.0,.65,1.0),d*vec3(1.0,.65,1.0),vec3(0),vec3(0,0,1),.56,.009,limit);
        }
    }else if(mechanic==12){ // wall: tiled barrier with upward scan bands
        float denom=d.z;
        if(abs(denom)>1e-7){
            float t=-o.z/denom;vec3 p=o+d*t;
            if(t>.015&&t<limit&&abs(p.x)<.9&&abs(p.y)<.85){
                float grid=min(abs(sin(p.x*14.0)),abs(sin(p.y*12.0)));
                float border=ink(abs(p.x)-.88,.010)+ink(abs(p.y)-.83,.010);
                light+=ink(grid,.025)*.65+border*.85+ink(p.y-mix(-.8,.8,fract(age*.55)),.013);
            }
        }
    }else if(mechanic==13){
        light+=shell(ro,rd,.84,1,time,limit);
    }else if(mechanic==14){ // summon: a three-sided rising construction frame
        for(int i=0;i<3;i++){
            float a=float(i)*TAU/3.0+time*.25,b=a+TAU/3.0;
            vec3 p=vec3(cos(a)*.62,.12,sin(a)*.62),q=vec3(cos(b)*.62,.12,sin(b)*.62);
            light+=wire(ro,rd,p,q,.008,limit)*.6;
            light+=wire(ro,rd,p,p+vec3(0,.8*smoothstep(0.0,.4,age),0),.010,limit);
        }
    }else if(mechanic==15){ // mark: four inward brackets and a hovering target indicator
        for(int i=0;i<4;i++){
            float a=float(i)*TAU/4.0+time*.15;vec3 side=vec3(cos(a),0,sin(a));
            light+=wire(ro,rd,side*.65,side*.48+vec3(0,.22,0),.009,limit);
            light+=wire(ro,rd,side*.48+vec3(0,.22,0),side*.30,.009,limit);
        }
        light+=halo(ro,rd,vec3(0,.78+sin(age*3.0)*.05,0),vec3(0,1,0),.18,.008,limit);
    }else if(mechanic==16){ // drain: pulses move from the target back toward the source
        for(int i=0;i<3;i++){
            float t=1.0-fract(age*1.5+float(i)/3.0);
            vec3 p=vec3(0,0,mix(-h*.9,h*.9,t));
            light+=halo(o,d,p,vec3(0,0,1),.12+.05*sin(t*3.14159),.012,limit);
            light+=wire(o,d,p+vec3(-.19,0,.18),p-vec3(0,0,.12),.008,limit);
            light+=wire(o,d,p+vec3(.19,0,.18),p-vec3(0,0,.12),.008,limit);
        }
    }else if(mechanic==17){ // channel: fixed focusing collars and rotating containment rails
        for(int i=0;i<3;i++){
            float z=mix(-h*.85,h*.85,float(i)*.5);
            light+=halo(o,d,vec3(0,0,z),vec3(0,0,1),.35,.008,limit)*.7;
            float a=float(i)*TAU/3.0+age;
            light+=wire(o,d,vec3(cos(a)*.35,sin(a)*.35,-h*.85),vec3(cos(a)*.35,sin(a)*.35,h*.85),.006,limit)*.5;
        }
    }else if(mechanic==18){ // echo: staggered reappearing wave rings
        for(int i=0;i<3;i++){
            float t=fract(age*.7+float(i)/3.0);
            light+=halo(ro,rd,vec3(0,.05+float(i)*.12,0),vec3(0,1,0),.2+t*.7,.010,limit)*(1.0-t);
        }
    }else{ // domain: rising latitude bands, open upper hemisphere and an exact ground rim
        float opening=smoothstep(0.0,.45,age);
        light+=shell(ro,rd,max(.12,.94*opening),2,time,limit);
        light+=halo(ro,rd,vec3(0),vec3(0,1,0),.96*max(.1,opening),.008,limit);
    }
    return min(light,3.0);
}
float exaltedDetail(vec3 ro,vec3 rd,float tier,int phase,float age,float life,float limit) {
    if(tier<5.0)return 0.0;
    float opening=phase==0?smoothstep(0.0,.65,life):phase==3?1.0-smoothstep(.1,1.0,life):smoothstep(0.0,.22,age);
    float scale=mix(.12,1.0,opening),light=0.0;
    int count=tier>7.5?3:2;
    for(int i=0;i<3;i++){
        if(i>=count)break;
        float a=float(i)*TAU/3.0+age*(i==1?-.35:.25);
        vec3 normal=normalize(vec3(cos(a)*.7,.6,sin(a)*.7));
        light+=halo(ro,rd,vec3(0,.16*float(i)*scale,0),normal,(.65+float(i)*.14)*scale,.0045,limit)*.65;
    }
    if(tier>7.5)for(int i=0;i<6;i++){
        float a=float(i)*TAU/6.0+age*.18,b=a+TAU/6.0;
        vec3 foot=vec3(cos(a)*.78,0,sin(a)*.78)*scale;
        vec3 top=vec3(cos(a)*.55,.95,sin(a)*.55)*scale;
        vec3 next=vec3(cos(b)*.55,.95,sin(b)*.55)*scale;
        light+=wire(ro,rd,foot,top,.005,limit)*.5+wire(ro,rd,top,next,.006,limit)*.7;
    }
    return light*opening*(phase==2?1.0-life*.6:1.0);
}
// Cosmetic combat geometry shares the existing depth-tested native-resolution pass.
// x is the hot edge, y the coloured secondary edge. All structures fit scene bounds.
vec2 combatDetail(vec3 ro,vec3 rd,vec3 axis,float halfSpan,int kind,float age,float life,float seed,float limit) {
    float light=0.0,accent=0.0;
    if(kind<3){
        vec3 helper=abs(axis.y)>.95?vec3(1,0,0):vec3(0,1,0);
        vec3 u=normalize(cross(helper,axis)),v=cross(axis,u);
        vec3 o=vec3(dot(ro,u),dot(ro,v),dot(ro,axis)),d=vec3(dot(rd,u),dot(rd,v),dot(rd,axis));
        float h=max(halfSpan,.08);
        if(kind==0){ // five connected flame lobes taper along the actual barrel direction
            float flare=(.18+.38*(1.0-life))*(.94+.06*sin(age*43.0));
            for(int i=0;i<5;i++){
                float a=float(i)*TAU/5.0+seed*.17;
                vec3 side=vec3(cos(a),sin(a),0);
                vec3 root=vec3(0,0,-h*.85),shoulder=side*flare+vec3(0,0,-h*.1);
                vec3 neck=side*(flare*.32)+vec3(0,0,h*.48),tip=vec3(0,0,h*.98);
                light+=wire(o,d,root,shoulder,.014,limit)*.8+wire(o,d,shoulder,neck,.010,limit);
                accent+=wire(o,d,neck,tip,.018,limit)*1.2;
            }
            light+=halo(o,d,vec3(0,0,h*.35),vec3(0,0,1),flare*.3,.012,limit)*.6;
        }else if(kind==1){ // a travelling compression diamond, confined to both shot endpoints
            float front=mix(-h,h,smoothstep(0.0,.82,life));
            float tail=max(-h,front-min(h*.45,3.5)),middle=mix(tail,front,.64);
            float width=.42*smoothstep(0.0,.08,life);
            for(int i=0;i<4;i++){
                float a=float(i)*TAU/4.0+seed*.11;
                vec3 shoulder=vec3(cos(a)*width,sin(a)*width,middle);
                light+=wire(o,d,vec3(0,0,tail),shoulder,.022,limit)*.6;
                light+=wire(o,d,shoulder,vec3(0,0,front),.026,limit);
            }
            accent+=halo(o,d,vec3(0,0,middle),vec3(0,0,1),width,.020,limit)*.65;
        }else{ // impact: closed ricochet shards bend back along the incoming shot
            float expand=.16+sqrt(life)*.78;
            for(int i=0;i<7;i++){
                float a=float(i)*TAU/7.0+seed*.13;
                vec3 side=vec3(cos(a),sin(a),0),tangent=vec3(-sin(a),cos(a),0);
                vec3 root=side*(expand*.16),tip=side*expand+vec3(0,0,life*.45);
                vec3 bend=side*(expand*.7)+tangent*.07+vec3(0,0,life*.30+.06);
                light+=(wire(o,d,root,bend,.009,limit)+wire(o,d,bend,tip,.013,limit))*(1.0-life);
                accent+=wire(o,d,root,tip,.008,limit)*(1.0-life);
            }
            accent+=halo(o,d,vec3(0,0,.035),vec3(0,0,1),expand*.65,.012,limit)*(1.0-life);
        }
    }else if(kind==3){ // expanding pressure shell: both intersections also work from inside
        float radius=.15+.88*sqrt(life),aa=dot(rd,rd),b=dot(ro,rd);
        float discriminant=b*b-aa*(dot(ro,ro)-radius*radius);
        if(discriminant>=0.0)for(int side=0;side<2;side++){
            float t=(-b+(side==0?-1.0:1.0)*sqrt(discriminant))/aa;
            if(t<=.015||t>=limit)continue;
            vec3 p=(ro+rd*t)/radius;
            float a=atan(p.z,p.x),latitude=asin(clamp(p.y,-1.0,1.0));
            float fractures=ink(sin(a*7.0+latitude*3.0+seed*.13),.022)*smoothstep(.18,.55,abs(p.y));
            float front=ink(latitude-.18*sin(a*5.0+age*4.0),.012);
            light+=(fractures*.55+front)*(1.0-life*.6);
        }
        accent+=halo(ro,rd,vec3(0,-.12,0),vec3(0,1,0),.18+sqrt(life)*1.10,.010,limit);
        accent+=halo(ro,rd,vec3(0,.06,0),vec3(0,1,0),.10+sqrt(life)*.82,.006,limit)*.7;
    }else if(kind==4){ // supply beacon: anchored braces, climbing scan collars and a top diamond
        float foot=-halfSpan,top=halfSpan;
        for(int i=0;i<4;i++){
            float a=float(i)*TAU/4.0+age*.22;
            vec3 side=vec3(cos(a),0,sin(a)),corner=side*.30+vec3(0,top-.15,0);
            light+=wire(ro,rd,corner,vec3(0,top+.36,0),.009,limit);
            light+=wire(ro,rd,corner,vec3(0,top-.66,0),.007,limit)*.7;
            accent+=wire(ro,rd,side*1.04+vec3(0,foot,0),side*.65+vec3(0,foot+.38,0),.013,limit);
        }
        for(int i=0;i<3;i++){
            float travel=fract(age*.36+float(i)/3.0),y=mix(foot,top,travel);
            float strength=smoothstep(0.0,.12,travel)*(1.0-smoothstep(.85,1.0,travel));
            light+=halo(ro,rd,vec3(0,y,0),vec3(0,1,0),.30,.010,limit)*strength;
            accent+=halo(ro,rd,vec3(0,y-.10,0),vec3(0,1,0),.37,.005,limit)*strength;
        }
    }else if(kind==5){ // a real hinged holographic cache; its lid opens before the reward rises
        vec3 o=ro,d=rd;o.xz=turn(age*.25)*o.xz;d.xz=turn(age*.25)*d.xz;
        for(int i=0;i<4;i++){
            float a=TAU/8.0+float(i)*TAU/4.0,b=a+TAU/4.0;
            vec3 low=vec3(cos(a)*.72,-.42,sin(a)*.72),next=vec3(cos(b)*.72,-.42,sin(b)*.72);
            light+=wire(o,d,low,next,.008,limit)+wire(o,d,low+vec3(0,.52,0),next+vec3(0,.52,0),.009,limit);
            accent+=wire(o,d,low,low+vec3(0,.52,0),.010,limit);
        }
        float angle=smoothstep(.04,.34,life)*1.65;
        vec3 hinge=vec3(0,.10,-.51),front=hinge+vec3(0,sin(angle)*1.02,cos(angle)*1.02),side=vec3(.51,0,0);
        light+=wire(o,d,hinge-side,hinge+side,.010,limit)+wire(o,d,front-side,front+side,.012,limit);
        light+=wire(o,d,hinge-side,front-side,.010,limit)+wire(o,d,hinge+side,front+side,.010,limit);
        float rise=smoothstep(.18,.60,life),y=.28+rise*.75;
        for(int i=0;i<4;i++){
            float a=float(i)*TAU/4.0+age*.8;vec3 corner=vec3(cos(a)*.18,y,sin(a)*.18);
            accent+=(wire(o,d,corner,vec3(0,y+.23,0),.013,limit)+wire(o,d,corner,vec3(0,y-.23,0),.009,limit))*rise;
        }
    }else if(kind==9){ // boss arrival: broken spires and an eclipse split by a serrated seam
        float opening=smoothstep(0.0,.26,life),spin=age*.15;
        for(int i=0;i<6;i++){
            float a=float(i)*TAU/6.0+spin;
            vec3 side=vec3(cos(a),0,sin(a)),tangent=vec3(-sin(a),0,cos(a));
            vec3 low=side*.76+vec3(0,-.65,0),knee=side*.92+vec3(0,.06,0);
            vec3 tip=side*.65+vec3(0,(i%2==0?1.10:.70)*opening,0);
            light+=wire(ro,rd,low,knee,.009,limit)+wire(ro,rd,knee,tip,.012,limit);
            accent+=wire(ro,rd,tip,knee+tangent*.13,.008,limit)*.8;
        }
        vec3 normal=vec3(sin(spin),0,cos(spin));
        accent+=halo(ro,rd,vec3(0,.10,0),normal,.70*opening,.014,limit);
        for(int i=0;i<6;i++){
            float y=-.5+float(i)*.2,ny=y+.2;
            vec3 p=vec3(sin(float(i)*2.6+age)*.12,y,0),q=vec3(sin(float(i+1)*2.6+age)*.12,ny,0);
            light+=wire(ro,rd,p*opening,q*opening,.009,limit)*opening;
        }
    }else if(kind==10){ // victory: a rising eight-point crown with two fluttering award ribbons
        float rise=smoothstep(0.0,.32,life),base=.02+life*.2;
        for(int i=0;i<8;i++){
            float a=float(i)*TAU/8.0+age*.22,b=a+TAU/8.0,m=(a+b)*.5;
            vec3 low=vec3(cos(a)*.62,base,sin(a)*.62),next=vec3(cos(b)*.62,base,sin(b)*.62);
            vec3 tip=vec3(cos(m)*.78,base+(i%2==0?.62:.40)*rise,sin(m)*.78);
            light+=wire(ro,rd,low,tip,.011,limit)+wire(ro,rd,tip,next,.011,limit);
            accent+=wire(ro,rd,low,next,.014,limit);
        }
        for(int side=-1;side<=1;side+=2)for(int i=0;i<4;i++){
            float t=float(i)*.25,n=t+.25,s=float(side);
            vec3 p=vec3(s*(.25+.12*sin(t*5.0+age*2.0)),base-t*.85,0);
            vec3 q=vec3(s*(.25+.12*sin(n*5.0+age*2.0)),base-n*.85,0);
            accent+=wire(ro,rd,p,q,.015,limit)*rise;
            light+=wire(ro,rd,p+vec3(s*.10,0,0),q+vec3(s*.10,0,0),.006,limit)*rise;
        }
        light+=halo(ro,rd,vec3(0,base-.10,0),vec3(0,1,0),.60,.008,limit);
    }
    return min(vec2(light,accent),vec2(3));
}
// Recovery events use distinct surfaces and timelines. Intersect both sides of
// each shell so a first-person camera inside the effect retains the far surface.
vec2 restorationDetail(vec3 ro,vec3 rd,int kind,float age,float life,float limit) {
    if(kind!=6&&kind!=7&&kind!=8&&kind!=11)return vec2(0);
    // Grazing plane intersections can have huge derivatives far from the surface.
    // Clip to the actual bounded recovery volume before evaluating antialiased ink.
    float boundsAa=dot(rd,rd),boundsB=dot(ro,rd),boundsDisc=boundsB*boundsB-boundsAa*(dot(ro,ro)-1.4*1.4);
    if(boundsDisc<0.0)return vec2(0);
    float boundsFar=(-boundsB+sqrt(boundsDisc))/boundsAa;
    if(boundsFar<=.015)return vec2(0);
    limit=min(limit,boundsFar);
    float light=0.0,accent=0.0;
    if(kind==6){ // three stitched ribbons wrap the body; green crosses mark actual healing
        float radius=.54,aa=dot(rd.xz,rd.xz),b=dot(ro.xz,rd.xz);
        float discriminant=b*b-aa*(dot(ro.xz,ro.xz)-radius*radius);
        if(aa>1e-8&&discriminant>=0.0)for(int side=0;side<2;side++){
            float t=(-b+(side==0?-1.0:1.0)*sqrt(discriminant))/aa;
            if(t<=.015||t>=limit)continue;
            vec3 p=ro+rd*t;float cap=1.0-smoothstep(.55,.70,abs(p.y));
            float braid=sin((atan(p.z,p.x)-p.y*4.4+age*2.8)*1.5);
            light+=ink(abs(braid)-.16,.020)*cap*.7;
            accent+=ink(sin(p.y*22.0-age*5.0),.065)*(1.0-smoothstep(.10,.16,abs(braid)))*cap;
        }
        for(int i=0;i<3;i++){
            float a=float(i)*TAU/3.0+age*.35;vec3 normal=vec3(cos(a),0,sin(a)),u=vec3(-sin(a),0,cos(a));
            vec3 center=normal*.67+vec3(0,-.08+life*.32,0);float denom=dot(rd,normal);
            if(abs(denom)<1e-7)continue;
            float t=dot(center-ro,normal)/denom;if(t<=.015||t>=limit)continue;
            vec3 p=ro+rd*t-center;vec2 q=vec2(dot(p,u),p.y);
            float crossShape=min(max(abs(q.x)-.045,abs(q.y)-.16),max(abs(q.x)-.16,abs(q.y)-.045));
            light+=ink(crossShape,.006)*.65;
            accent+=(1.0-smoothstep(-.006,.006,crossShape))*.45;
        }
        accent+=halo(ro,rd,vec3(0,-.67,0),vec3(0,1,0),.54,.006,limit)*.6;
    }else if(kind==7){ // a spherical reservoir with a rising energy surface and inward currents
        float radius=.58,aa=dot(rd,rd),b=dot(ro,rd),discriminant=b*b-aa*(dot(ro,ro)-radius*radius);
        float fill=mix(-.46,.42,smoothstep(.03,.86,life));
        if(discriminant>=0.0)for(int side=0;side<2;side++){
            float t=(-b+(side==0?-1.0:1.0)*sqrt(discriminant))/aa;
            if(t<=.015||t>=limit)continue;
            vec3 p=ro+rd*t;float a=atan(p.z,p.x),latitude=asin(clamp(p.y/radius,-1.0,1.0));
            float waterline=p.y-fill-.028*sin(a*5.0+age*4.0);
            light+=ink(waterline,.009)*1.1;
            accent+=ink(sin(a*3.0+age*.25),.022)*.35;
            accent+=ink(sin(latitude*7.0),.018)*step(p.y,fill)*.3;
        }
        if(abs(rd.y)>1e-7){
            float t=(-.65-ro.y)/rd.y;vec3 p=ro+rd*t;float r=length(p.xz),a=atan(p.z,p.x);
            if(t>.015&&t<limit){
                float spiral=ink(sin(a*3.0+r*10.0+age*4.0),.035);
                accent+=spiral*smoothstep(.12,.23,r)*(1.0-smoothstep(.76,.86,r));
                light+=ink(r-.20,.008)*.5;
            }
        }
    }else if(kind==8){ // six feather-shaped surfaces tilt outward from an upright recovery cocoon
        float opening=smoothstep(.12,.62,life);
        for(int i=0;i<6;i++){
            float a=float(i)*TAU/6.0+age*.16;vec3 side=vec3(cos(a),0,sin(a)),u=vec3(-sin(a),0,cos(a));
            vec3 v=normalize(side*(.2+.9*opening)+vec3(0,1,0)),normal=cross(u,v);
            vec3 center=side*(.28+.37*opening)+vec3(0,.10,0);
            float denom=dot(rd,normal);if(abs(denom)<1e-7)continue;
            float t=dot(center-ro,normal)/denom;if(t<=.015||t>=limit)continue;
            vec3 p=ro+rd*t-center;vec2 q=vec2(dot(p,u),dot(p,v));
            float width=.14+.08*opening,shape=abs(q.x)/width+abs(q.y)/.70;
            light+=ink(shape-1.0,.025)*.9;
            if(shape<1.0){
                light+=ink(q.x,.004)*.6;
                accent+=ink(sin((q.y-abs(q.x)*2.0)*20.0),.065)*.6;
            }
        }
        light+=halo(ro,rd,vec3(0,-.62,0),vec3(0,1,0),.25+opening*.28,.008,limit)*.7;
        accent+=halo(ro,rd,vec3(0,-.62,0),vec3(0,1,0),.34+opening*.35,.005,limit)*.6;
    }else if(kind==11){ // a closed rotating octahedron with three independently inclined satellite tracks
        vec3 o=ro,d=rd;float lift=.06+sin(age*2.0)*.06;o.y-=lift;
        o.xz=turn(age*.45)*o.xz;d.xz=turn(age*.45)*d.xz;
        for(int i=0;i<4;i++){
            float a=float(i)*TAU/4.0,b=a+TAU/4.0;
            vec3 edge=vec3(cos(a)*.38,0,sin(a)*.38),next=vec3(cos(b)*.38,0,sin(b)*.38);
            light+=wire(o,d,edge,vec3(0,.72,0),.009,limit)+wire(o,d,edge,vec3(0,-.72,0),.007,limit);
            accent+=wire(o,d,edge,next,.006,limit)*.7;
        }
        for(int i=0;i<3;i++){
            float a=float(i)*TAU/3.0-age*.18;vec3 normal=normalize(vec3(cos(a)*.75,.45,sin(a)*.75));
            vec3 u=normalize(cross(vec3(0,1,0),normal)),v=cross(normal,u);
            float denom=dot(rd,normal);if(abs(denom)<1e-7)continue;
            vec3 center=vec3(0,lift,0);float t=dot(center-ro,normal)/denom;if(t<=.015||t>=limit)continue;
            vec3 p=ro+rd*t-center;vec2 q=vec2(dot(p,u),dot(p,v));float radius=.78+float(i)*.09;
            accent+=ink(length(q)-radius,.0045)*.65;
            float spin=age*(i==1?-1.1:.8)+float(i)*2.1;
            vec2 satellite=q-vec2(cos(spin),sin(spin))*radius;
            light+=ink(abs(satellite.x)+abs(satellite.y)-.066,.007);
        }
    }
    return min(vec2(light,accent)*(kind==6?1.45:kind==7?1.20:1.0),vec2(2.8));
}
float journeyDetail(vec3 ro,vec3 rd,int kind,float age,float life,float limit) {
    float light=0.0;
    if(kind==18){ // written lines on both opening pages, and a rising knowledge diamond
        vec3 o=ro,d=rd;o.xz=turn(age*.35)*o.xz;d.xz=turn(age*.35)*d.xz;
        float spread=.12+.62*smoothstep(0.0,.22,life);
        for(int side=-1;side<=1;side+=2)for(int i=0;i<4;i++){
            float z=-.24+float(i)*.16,s=float(side),end=spread*(i==3?.68:.87);
            light+=wire(o,d,vec3(s*spread*.17,-.216,z),vec3(s*end,-.25+end*.2/spread,z),.006,limit);
        }
        float y=.28+life*.48;
        for(int i=0;i<4;i++){
            float a=float(i)*TAU/4.0+age*.25,b=a+TAU/4.0;
            light+=wire(o,d,vec3(cos(a)*.15,y+sin(a)*.15,0),vec3(cos(b)*.15,y+sin(b)*.15,0),.009,limit);
        }
    }else if(kind==19){ // an ascending staircase; growth is distinct from a refill's helix
        for(int i=0;i<8;i++){
            float a=float(i)*TAU/8.0+age*.45,y=-.5+float(i)*.14;
            vec3 side=vec3(cos(a),0,sin(a)),tangent=vec3(-sin(a),0,cos(a));
            vec3 p=side*.42+vec3(0,y,0),q=p+tangent*.18;
            light+=wire(ro,rd,p,q,.008,limit)+wire(ro,rd,q,q+vec3(0,.14,0),.006,limit)*.6;
        }
    }else if(kind==20){ // connected interior fissure, revealed from feet to head
        float open=smoothstep(0.0,.18,life)*(1.0-smoothstep(.5,1.0,life));
        for(int i=0;i<7;i++){
            float y=float(i)*.17,ny=y+.17;
            vec3 p=vec3(sin(float(i)*2.7+age)*.07*open,y,.025),q=vec3(sin(float(i+1)*2.7+age)*.07*open,ny,.025);
            light+=wire(ro,rd,p,q,.010,limit)*smoothstep(y-.1,y+.1,life*5.0);
        }
    }else if(kind==21){ // leaf veins and the small completion seal
        for(int side=-1;side<=1;side+=2)for(int i=0;i<4;i++){
            float a=float(i)*.34-.6,s=float(side);
            vec3 p=vec3(s*(.48+cos(a*2.0)*.16),a+life*.18,0);
            light+=wire(ro,rd,p+vec3(0,.05,.01),p+vec3(s*.14,.17,.07),.006,limit);
        }
        light+=halo(ro,rd,vec3(0,.8,0),vec3(0,0,1),.27,.005,limit);
    }else if(kind==22){ // a cartographic compass with moving convergence indicators
        light+=halo(ro,rd,vec3(0),vec3(0,1,0),.98,.005,limit);
        for(int i=0;i<12;i++){
            float a=float(i)*TAU/12.0;vec3 side=vec3(cos(a),0,sin(a));
            light+=wire(ro,rd,side*.90,side*(i%3==0?1.04:.98),.005,limit)*.7;
        }
        for(int i=0;i<3;i++){
            float a=float(i)*TAU/3.0+age*.2,t=fract(life*1.8);
            vec3 p=mix(vec3(cos(a)*.75,.02,sin(a)*.75),vec3(0,.55,0),t);
            light+=halo(ro,rd,p,vec3(0,1,0),.07,.009,limit);
        }
    }else if(kind==23){ // low triangular exclamation glyphs, only sent to their owner
        for(int i=0;i<3;i++){
            float a=float(i)*TAU/3.0,spin=age*.12;
            vec3 normal=vec3(cos(a+spin),0,sin(a+spin)),side=vec3(-normal.z,0,normal.x);
            vec3 center=normal*.65+vec3(0,-.12,0),top=center+vec3(0,.25,0);
            vec3 left=center-side*.19-vec3(0,.09,0),right=center+side*.19-vec3(0,.09,0);
            light+=wire(ro,rd,top,left,.008,limit)+wire(ro,rd,left,right,.008,limit)+wire(ro,rd,right,top,.008,limit);
            light+=wire(ro,rd,center+vec3(0,.13,0),center+vec3(0,.035,0),.010,limit);
            light+=halo(ro,rd,center-vec3(0,.025,0),normal,.012,.006,limit);
        }
    }
    return min(light,2.5);
}
void main() {
    vec2 uv=gl_FragCoord.xy/ViewportTime.xy;
    bool zeroToOne=mod(ViewportTime.w,2.0)>0.5,reversedDepth=ViewportTime.w<1.5;
    vec4 farPoint=InvViewProjection*vec4(uv*2.0-1.0,reversedDepth?(zeroToOne?0.0:-1.0):1.0,1.0);
    vec3 ray=normalize(farPoint.xyz/(abs(farPoint.w)<1e-7?1e-7:farPoint.w));
    float depth=texture(SceneDepth,uv).r,limit=1e6;
    if(reversedDepth?depth>1e-7:depth<0.9999999){
        vec4 p=InvViewProjection*vec4(uv*2.0-1.0,zeroToOne?depth:depth*2.0-1.0,1.0);
        if(abs(p.w)>1e-7)limit=max(0.0,dot(p.xyz/p.w,ray)-0.008);
    }
    float radius=CenterRadius.w,age=max(0.0,Animation.x),life=clamp(age/max(Animation.y,0.05),0.0,1.0);
    vec3 ro=-CenterRadius.xyz/radius,rd=ray/radius;
    vec3 axis=normalize(AxisExtent.xyz+vec3(0,1e-6,0));
    vec3 tint=TintTier.rgb,hot=mix(tint,vec3(1),0.72);
    bool world=Identity.w>0.5;
    int kind=int(Identity.x+0.5),phase=int(Animation.z+0.5),mechanic=int(Animation.w+0.5);
    float seed=Identity.z,time=age+mod(seed*1.618,10.0),light=0.0,accent=0.0;
    float fade=1.0-smoothstep(0.72,1.0,life);
    if(world&&kind<2)fade=pow(1.0-life,0.8);
    if(world && (kind==4||kind==16))fade=1.0;
    if(world&&kind>=18)light+=journeyDetail(ro,rd,kind,age,life,limit)*1.4;
    if(!world){
        if(phase==1)light+=deliveryDetail(ro,rd,axis,AxisExtent.w/radius,mechanic,age,life,seed,limit)*(.56+TintTier.w*.035);
        accent+=exaltedDetail(ro,rd,TintTier.w,phase,age,life,limit)*1.1;
    }
    // Weapon cores and beacon spines: finite segments, clipped at world depth.
    if(world && (kind==0||kind==1||kind==4)){
        float halfSpan=AxisExtent.w/radius;
        vec2 hit=segment(ro,rd,-axis*halfSpan,axis*halfSpan);
        if(hit.y>0.015&&hit.y<limit){
            light=ink(hit.x,kind==1?0.018:kind==4?0.025:0.06);
            accent=ink(hit.x,kind==1?0.05:0.11)*0.16;
        }
    }
    if(!world&&phase==1&&(mechanic==0||mechanic==1||mechanic==5||mechanic==10||mechanic==16||mechanic==17)){
        float halfSpan=AxisExtent.w/radius;
        vec2 hit=segment(ro,rd,-axis*halfSpan*0.96,axis*halfSpan*0.96);
        if(hit.y>0.015&&hit.y<limit){
            light+=ink(hit.x,0.012)*(0.22+TintTier.w*0.035);
            accent+=ink(hit.x-0.045,0.003)*0.12;
        }
    }
    // Engraved discs carry legible symbols instead of an undifferentiated glow.
    bool area=mechanic==6||mechanic==7||mechanic==9||mechanic==12||mechanic==13||mechanic==14||mechanic==15||mechanic==18||mechanic==19;
    bool seal=!world&&(phase==0||phase==2||phase==3||(phase==1&&area)||TintTier.w>7.5);
    bool worldSeal=world&&kind>=4&&kind<18&&kind!=6&&kind!=7&&kind!=8&&kind!=11;
    float plane=world?(kind==4?-AxisExtent.w/radius:kind>=12?0.0:-0.72):0.0;
    if((seal||worldSeal)&&abs(rd.y)>1e-7){
        float t=(plane-ro.y)/rd.y;
        vec3 p=ro+rd*t;
        float r=length(p.xz),a=atan(p.z,p.x);
        float expansion=phase==2&&!world?mix(0.3,1.1,sqrt(life)):1.0;
        vec2 uvSeal=p.xz/expansion;
        r=length(uvSeal);
        if(t>0.015&&t<limit&&r<1.16){
            float bands=ink(r-0.97,0.0025)+ink(r-0.90,0.0020)*0.65;
            float segments=abs(fract((a+time*0.13)/TAU*36.0)-0.5);
            float ticks=ink(r-0.936,0.017)*ink(segments,0.055);
            // Each school has its own count and rune strokes; seed rotates the inscription.
            float count=12.0+mod(float(kind),5.0)*2.0;
            float cell=fract((a-time*0.09+seed*0.07)/TAU*count)-0.5;
            vec2 rune=vec2(cell*3.0,(r-0.77)*17.0);
            float stem=ink(rune.x,0.035)*step(abs(rune.y),0.70);
            float cross=ink(rune.y-(mod(float(kind),3.0)-1.0)*rune.x,0.035)*step(abs(rune.x),0.45);
            float fork=ink(abs(rune.x)-0.30+0.25*rune.y,0.035)*step(abs(rune.y),0.6);
            float runes=(stem+cross+fork)*step(abs(r-0.77),0.05);
            float petals=ink(r-(0.42+0.10*cos(a*(3.0+mod(float(kind),5.0))-time)),0.003);
            light+=(bands+ticks*0.85+runes*0.6+petals*0.5)*(world?0.7:0.38+TintTier.w*0.045);
            if(!world&&phase==1)light+=schoolMark(uvSeal,kind,time,seed)*(.38+TintTier.w*.025);
        }
    }
    // Full-resolution facet edges on shields and domains.
    if(!world&&phase==1&&(mechanic==13||mechanic==19)){
        float b=dot(ro,rd),aa=dot(rd,rd),disc=b*b-aa*(dot(ro,ro)-0.83*0.83);
        if(disc>=0.0){
            float t=(-b-sqrt(disc))/aa;
            vec3 n=normalize(ro+rd*t);
            float latitude=asin(clamp(n.y,-1.0,1.0)),longitude=atan(n.z,n.x);
            float grid=min(abs(sin(longitude*8.0+latitude*7.0)),abs(sin(longitude*8.0-latitude*7.0)));
            if(t>0.015&&t<limit)light+=ink(grid,0.015)*0.25;
        }
    }
    // Closed radial slashes accent hits; no sprites or point emitters.
    if((world&&kind==2)||(!world&&phase==2)){
        vec3 helper=abs(axis.y)>0.95?vec3(1,0,0):vec3(0,1,0);
        vec3 u=normalize(cross(axis,helper)),v=cross(axis,u);
        float denom=dot(rd,axis);
        if(abs(denom)>1e-7){
            float t=-dot(ro,axis)/denom;vec3 p=ro+rd*t;
            vec2 q=vec2(dot(p,u),dot(p,v));float r=length(q),a=atan(q.y,q.x);
            float spokes=ink(sin(a*(5.0+mod(seed,4.0))+seed),0.018)*smoothstep(0.12,0.24,r)*(1.0-smoothstep(0.6,0.9,r));
            if(t>0.015&&t<limit){
                light+=spokes*(1.0-life)*(world?.8:.25);
                if(!world)light+=schoolMark(q/mix(.45,1.08,sqrt(life)),kind,time,seed)*(1.0-life)*1.25;
            }
        }
    }
    vec2 combat=world?combatDetail(ro,rd,axis,AxisExtent.w/radius,kind,age,life,seed,limit):vec2(0);
    light+=combat.x;accent+=combat.y;
    vec2 restoration=world?restorationDetail(ro,rd,kind,age,life,limit):vec2(0);
    light+=restoration.x;accent+=restoration.y;
    float onset=world?1.0:smoothstep(0.0,0.08,age);
    vec3 color=(hot*light+tint*accent)*fade*onset*(world?0.45:0.33);
    if(world) color *= Dynamics.z;
    color=vec3(1)-exp(-min(color,vec3(3)));
    if(max(color.r,max(color.g,color.b))<0.0005)discard;
    fragColor=vec4(color,0);
}
