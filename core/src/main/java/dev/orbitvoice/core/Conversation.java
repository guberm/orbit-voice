package dev.orbitvoice.core;
import com.google.gson.*;
import java.util.*;
public class Conversation {
    public String id = UUID.randomUUID().toString();
    public long createdAt = System.currentTimeMillis();
    public String title = "New conversation";
    public JsonArray context = new JsonArray();
    public List<Turn> turns = new ArrayList<>();
    public static class Turn { public String question,answer; public long at; public Turn(String q,String a) { question=q;answer=a;at=System.currentTimeMillis(); } }
    private static JsonObject message(String role,String text) { JsonObject m=new JsonObject(); m.addProperty("role",role);m.addProperty("content",text);return m; }
    public JsonObject request(String model, String question) {
        if (model==null || model.trim().isEmpty()) throw new IllegalArgumentException("Choose an available model on your phone.");
        if (question==null || question.trim().isEmpty() || question.length()>4000) throw new IllegalArgumentException("Please use a shorter spoken question.");
        if (context.toString().length()>150000) throw new IllegalArgumentException("This conversation is long. Start a new conversation to continue.");
        JsonObject body=new JsonObject(); body.addProperty("model",model); body.addProperty("store",false);body.addProperty("stream",true);
        body.addProperty("instructions","You are Orbit Voice, an independent voice assistant on a smartwatch. Respond in the user's language. Keep answers concise and natural for speech, normally under 250 words. Avoid markdown formatting and tables unless asked. Preserve conversational context. Do not claim to be the official ChatGPT app or to sync with ChatGPT history.");
        JsonArray input=context.deepCopy(); input.add(message("user",question.trim()));body.add("input",input);return body;
    }
    public void commit(String question, String answer, JsonArray output) {
        if (question==null || question.trim().isEmpty() || answer==null || answer.trim().isEmpty()) throw new IllegalArgumentException("Empty turn");
        context.add(message("user",question));
        if (output!=null && output.size()>0) for (JsonElement item : output) context.add(item.deepCopy());
        else context.add(message("assistant",answer));
        if (turns.isEmpty()) title=question.substring(0,Math.min(question.length(),64));
        turns.add(new Turn(question,answer));
    }
    public String markdown() {
        StringBuilder out=new StringBuilder("# Orbit Voice conversation\n\nLocal export. This conversation is not in ChatGPT history.\n\n");
        for (Turn t : turns) out.append("## You\n\n").append(t.question).append("\n\n## Orbit Voice\n\n").append(t.answer).append("\n\n");
        return out.toString();
    }
}
