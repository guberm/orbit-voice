package dev.orbitvoice.core;
import java.util.*;
public class SpeechBuffer {
    private String previous="";
    private int emitted;
    public List<String> update(String cumulative, boolean complete) {
        if (!cumulative.startsWith(previous)) throw new IllegalArgumentException("Response text changed while speaking");
        previous=cumulative; List<String> result=new ArrayList<>();
        while (emitted<cumulative.length()) {
            int remaining=cumulative.length()-emitted, boundary=-1;
            for (int i=emitted;i<Math.min(cumulative.length(),emitted+380);i++) {
                char ch=cumulative.charAt(i);
                if ((ch=='.'||ch=='!'||ch=='?'||ch=='\n') && i+1<cumulative.length() && Character.isWhitespace(cumulative.charAt(i+1))) { boundary=i+1;break; }
            }
            if (boundary<0 && remaining>380) {
                boundary=cumulative.lastIndexOf(' ',emitted+380);
                if (boundary<=emitted) { boundary=emitted+380; if (Character.isHighSurrogate(cumulative.charAt(boundary-1))) boundary--; }
            }
            if (boundary<0 && complete) boundary=cumulative.length();
            if (boundary<0) break;
            String part=cumulative.substring(emitted,boundary).trim(); emitted=boundary;
            if (!part.isEmpty()) result.add(part);
        }
        return result;
    }
}
