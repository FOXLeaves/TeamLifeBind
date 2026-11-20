#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 uiUv;
layout(location = 0) out vec4 fragColor;
layout(std140) uniform OrbitFrame {
    vec4 Canvas; // attachment width, height, elapsed active seconds, front-page emphasis
    vec4 Pointer; // smoothed parallax; never moves text or hit targets
};
const vec3 GOLD=vec3(.83,.65,.36), SILVER=vec3(.52,.73,.79);
float hash21(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise2(vec2 p){
    vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
    return mix(mix(hash21(i),hash21(i+vec2(1,0)),f.x),mix(hash21(i+vec2(0,1)),hash21(i+1.),f.x),f.y);
}
float mist(vec2 p){
    float v=0.,a=.55;
    for(int i=0;i<4;i++){v+=noise2(p)*a;p=mat2(1.62,1.17,-1.17,1.62)*p+3.7;a*=.46;}
    return v;
}
mat3 rotateY(float a){float c=cos(a),s=sin(a);return mat3(c,0,-s,0,1,0,s,0,c);}
float sphere(vec3 ro,vec3 rd,float radius){
    float b=dot(ro,rd),c=dot(ro,ro)-radius*radius,d=b*b-c;
    return d<0.?10000.:-b-sqrt(d);
}
vec3 stars(vec2 uv,float density,float time){
    vec2 grid=uv*vec2(density*Canvas.x/Canvas.y,density),cell=floor(grid),f=fract(grid);
    float seed=hash21(cell+15.3),exists=step(.991,seed);
    vec2 center=vec2(hash21(cell+2.7),hash21(cell+7.1))*.66+.17;
    float d=length(f-center),radius=max(length(fwidth(grid))*.26,.018);
    float core=1.-smoothstep(radius*.3,radius,d);
    float halo=exp(-d*35.)*.12;
    float breathe=.82+.18*sin(time*.18+seed*35.);
    return mix(SILVER,GOLD,seed)*exists*(core+halo)*breathe*.62;
}
vec3 orbitalRing(vec3 ro,vec3 rd,vec3 normal,float radius,float time,float depth,float identity){
    normal=normalize(rotateY(time*.018+identity*.32)*normal);
    float denom=dot(rd,normal);
    if(abs(denom)<.015)return vec3(0);
    float distance=-dot(ro,normal)/denom;
    if(distance<=0.||distance>depth+.003)return vec3(0);
    vec3 point=ro+rd*distance;
    float r=length(point),delta=abs(r-radius),aa=max(fwidth(r),.0015);
    vec3 tangent=normalize(cross(normal,abs(normal.y)>.85?vec3(1,0,0):vec3(0,1,0)));
    float angle=atan(dot(point,cross(normal,tangent)),dot(point,tangent));
    float arc=.36+.64*smoothstep(-.2,.75,sin(angle*2.+time*.07+identity));
    float line=1.-smoothstep(.003,.003+aa*1.3,delta);
    float companion=(1.-smoothstep(.002,.002+aa,abs(r-radius-.035)))*.24;
    float marks=pow(max(0.,cos(angle*72.+identity)),30.)*(1.-smoothstep(.014,.014+aa,abs(r-radius-.06)))*.5;
    float bead=exp((cos(angle-time*.055-identity*2.)-1.)*480.)*exp(-delta*100.);
    float halo=exp(-delta*55.)*.15;
    vec3 metal=mix(GOLD,SILVER,step(1.5,identity));
    return metal*((line*arc+companion+marks)*.82+halo+bead*2.3);
}
void main(){
    vec2 uv=uiUv,drift=Pointer.xy*.006;
    float time=Canvas.z,aspect=Canvas.x/Canvas.y,emphasis=.48+.52*Canvas.w;
    // Let the front-page instrument sit beside the buttons. As a child page opens,
    // the composition eases back under its translucent center panel.
    float titleWeight=clamp((Canvas.w-.35)/.65,0.,1.);
    vec2 orbitalCenter=mix(vec2(.47,.54),vec2(.735,.54),titleWeight);
    vec2 n=uv+drift;
    float clouds=mist(n*3.2+vec2(time*.008,-time*.004));
    float folds=mist(n*5.6+vec2(-time*.006,time*.003)+clouds*1.1);
    float plume=exp(-pow((n.x-orbitalCenter.x)*2.3+(n.y-.5)*.55,2.)*3.4);
    vec3 color=vec3(.012,.023,.042)+vec3(.022,.068,.082)*clouds*plume;
    color+=vec3(.018,.034,.065)*folds*folds;
    color+=stars(n,39.,time)+stars(n+vec2(.17,.31),73.,time)*.6;
    // A low mist pool catches the orbital light without introducing a world/background texture.
    float floorFade=(1.-smoothstep(.12,.40,uv.y))*smoothstep(0.,.10,uv.y);
    float pool=exp(-pow((uv.x-orbitalCenter.x)*2.8,2.))*floorFade;
    float ripples=.5+.5*sin(uv.x*23.+folds*4.+time*.10);
    color+=vec3(.025,.075,.084)*pool*(.35+.65*ripples)*emphasis;

    vec2 p=(uv-orbitalCenter)*vec2(aspect,1.)*mix(2.3,2.82,titleWeight);
    vec3 ro=vec3(Pointer.x*.10,-Pointer.y*.065,4.25),rd=normalize(vec3(p,-3.1));
    float depth=sphere(ro,rd,.68);
    float glow=exp(-dot(p,p)*1.7);
    color+=vec3(.014,.071,.080)*glow*emphasis;
    if(depth<1000.){
        vec3 normal=normalize(ro+rd*depth);
        vec3 spun=rotateY(time*.025)*normal;
        float light=max(dot(normal,normalize(vec3(-.7,.85,1.6))),0.);
        float fresnel=pow(1.-max(dot(normal,-rd),0.),3.2);
        float flow=mist(spun.xy*3.8+vec2(time*.011,-time*.009));
        float longitude=atan(spun.z,spun.x);
        float filament=1.-smoothstep(.025,.12,abs(sin(longitude*5.+spun.y*9.+flow*5.+time*.08)));
        float fine=1.-smoothstep(.012,.05,abs(sin(longitude*11.-spun.y*15.+flow*6.)));
        vec3 core=vec3(.014,.051,.073)*(.45+light)+vec3(.052,.23,.27)*filament*.57;
        core+=GOLD*fine*.16*light+SILVER*fresnel*.52;
        core+=vec3(.025,.065,.075)*flow*light;
        float edge=length(cross(ro,rd)),edgeAA=max(fwidth(edge)*1.3,.001);
        float coverage=1.-smoothstep(.68-edgeAA,.68,edge);
        color=mix(color,core,emphasis*coverage);
    }
    color+=orbitalRing(ro,rd,vec3(.28,.80,.43),1.02,time,depth,0.)*emphasis;
    color+=orbitalRing(ro,rd,vec3(.81,-.28,.49),1.24,time,depth,1.)*emphasis;
    color+=orbitalRing(ro,rd,vec3(-.32,.48,.82),1.42,time,depth,2.)*emphasis*.65;

    float vignette=1.-smoothstep(.32,1.1,length((uv-.5)*vec2(1.,.85)));
    color*=.53+.47*vignette;
    color*=mix(.69, .37, titleWeight)+mix(.31, .63, titleWeight)*smoothstep(.12,.62,uv.x);
    color=1.-exp(-color*1.16);
    color+=(hash21(gl_FragCoord.xy)-.5)/255.;
    fragColor=vec4(clamp(color,0.,1.),1.);
}
