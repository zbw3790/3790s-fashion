package dev.zbw3790.fashion.network;

import java.util.*;

/** 各资源域分别持有；计数先于授权和去重，更新授权不重置预算。 */
public final class AssetRequestBudget {
    private final int maximumUnique,maximumAttempts;
    private final Set<String> sent=new HashSet<>();
    private int attempts;private boolean exhausted;
    public AssetRequestBudget(int maximumUnique,int maximumAttempts) {
        if(maximumUnique<1 || maximumAttempts<maximumUnique) throw new IllegalArgumentException("资产预算无效。");
        this.maximumUnique=maximumUnique;this.maximumAttempts=maximumAttempts;
    }
    public List<String> claim(List<String> requested,Set<String> authorized) {
        if(exhausted) return List.of();
        if(requested.size()>maximumAttempts-attempts) {attempts=maximumAttempts;exhausted=true;return List.of();}
        attempts+=requested.size();var accepted=new LinkedHashSet<String>();
        for(String hash:requested) if(authorized.contains(hash) && !sent.contains(hash)) accepted.add(hash);
        if(accepted.size()>maximumUnique-sent.size()) {exhausted=true;return List.of();}
        sent.addAll(accepted);return List.copyOf(accepted);
    }
    public int attempts() {return attempts;}
    public int sentCount() {return sent.size();}
}
