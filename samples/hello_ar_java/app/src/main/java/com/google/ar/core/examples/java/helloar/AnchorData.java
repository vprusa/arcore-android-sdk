package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.Anchor;

public class AnchorData {

    public Anchor anchor;
    public int index;

    public AnchorData(Anchor anchor){
        this.anchor = anchor;
    }

    public AnchorData(Anchor anchor, int index){
        this.anchor = anchor;
        this.index = index;
    }

}
