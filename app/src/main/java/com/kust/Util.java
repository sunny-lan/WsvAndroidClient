package com.kust;

public final class Util {
    public static <T> T getDefault(T s, T d){
        if(s==null)
            return d;
        else
            return s;
    }

}
