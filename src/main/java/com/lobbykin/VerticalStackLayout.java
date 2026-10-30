package com.lobbykin;
import java.awt.*;
/** Natural-height sidebar stack. Hidden disclosures occupy no space. */
final class VerticalStackLayout implements LayoutManager {
 private final int gap;
 VerticalStackLayout(int gap){this.gap=gap;}
 public void addLayoutComponent(String name,Component component){}
 public void removeLayoutComponent(Component component){}
 public Dimension minimumLayoutSize(Container parent){return preferredLayoutSize(parent);}
 public Dimension preferredLayoutSize(Container parent){synchronized(parent.getTreeLock()){
  Insets insets=parent.getInsets();int width=Math.max(1,(parent.getWidth()>0?parent.getWidth():205)-insets.left-insets.right),height=0,count=0;
  for(Component child:parent.getComponents())if(child.isVisible()){child.setSize(width,child.getHeight());height+=child.getPreferredSize().height;count++;}
  return new Dimension(width+insets.left+insets.right,height+Math.max(0,count-1)*gap+insets.top+insets.bottom);
 }}
 public void layoutContainer(Container parent){synchronized(parent.getTreeLock()){
  Insets insets=parent.getInsets();int width=Math.max(1,parent.getWidth()-insets.left-insets.right),y=insets.top;
  for(Component child:parent.getComponents())if(child.isVisible()){child.setSize(width,child.getHeight());int height=child.getPreferredSize().height;child.setBounds(insets.left,y,width,height);y+=height+gap;}
 }}
}
