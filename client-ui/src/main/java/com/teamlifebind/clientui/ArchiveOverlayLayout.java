package com.teamlifebind.clientui;

import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;

/** Allocate independent reading regions; all geometry is measured in the document's own CSS viewport. */
final class ArchiveOverlayLayout {
    private ArchiveOverlayLayout(){}
    private static void style(Element element,String property,String value){if(!value.equals(element.getInlineStylePropertyValue(property)))element.setInlineStyleProperty(property,value);}
    private static void px(Element element,String property,double value){style(element,property,value+"px");}
    static void apply(Document document,boolean notice,boolean status,boolean chat,ArchiveHud.Rail rail){
        double width=document.getViewportSize().width(),height=document.getViewportSize().height();
        var sidebar=document.getElementById("sidebar");var announcements=document.getElementById("notifications");
        var bosses=document.getElementById("bosses");var boss=bosses.getBoundingClientRect();
        boolean bossActive=bosses.getChildElementCount()>0&&!bosses.getAttribute("style").contains("display:none")&&boss.height>1;
        double statusWidth=Math.min(260,width*.34),statusLeft=width-statusWidth-20;
        double statusTop=Math.max(28,rail.top());
        if(bossActive&&boss.right>statusLeft)statusTop=Math.max(statusTop,boss.bottom+10);
        if(status&&!HudLayout.custom(HudLayout.Part.STATUS)){
            px(sidebar,"width",statusWidth);px(sidebar,"right",20);style(sidebar,"left","auto");
            px(sidebar,"top",statusTop);px(sidebar,"max-height",Math.max(48,rail.bottom()-statusTop-10));
        }
        if(notice&&!HudLayout.custom(HudLayout.Part.NOTICES)){
            double noticeWidth=Math.min(300,width*.43),x=Math.max(16,Math.min((width-noticeWidth)/2,statusLeft-noticeWidth-16));
            px(announcements,"left",x);style(announcements,"right","auto");style(announcements,"transform","none");
            px(announcements,"width",noticeWidth);px(announcements,"top",Math.max(28,bossActive?boss.bottom+10:rail.top()));
        }
        if(chat&&notice&&!HudLayout.custom(HudLayout.Part.CHAT)){
            var messages=document.getElementById("hud-chat");var frame=messages.getBoundingClientRect();var card=announcements.getBoundingClientRect();
            if(frame.left<card.right&&frame.right>card.left&&frame.top<card.bottom+8&&frame.bottom>card.top){
                double available=frame.bottom-card.bottom-8;
                if(available>=40)px(messages,"height",available);
                else if(card.top-frame.top>=40){px(messages,"height",card.top-frame.top-8);px(messages,"bottom",height-card.top+8);}
            }
        }
    }
}
