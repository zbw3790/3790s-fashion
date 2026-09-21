package dev.zbw3790.fashion.client.screen;

/** 由 generate_armor_tab_logo.py 确定性生成；原创像素，不含 Mojang 素材。 */
final class ArmorTabIcon {
    private ArmorTabIcon() { }
    private static final String[] ROWS = {
        "................",
        "................",
        "...ccc....ccc...",
        "..cccba..abccc..",
        "..cbbbaaaabbbc..",
        "..abbbbbbbbbba..",
        "...abbbaabbba...",
        "....abbbbbba....",
        "....abbaabba....",
        "....abbbbbba....",
        "....abbaabba....",
        "....abbbbbba....",
        ".....abbbba.....",
        ".....aaaaaa.....",
        "................",
        "................",
    };
    static void paint(WardrobeGuiPainter.RectangleSink sink,WardrobeLayout.Bounds bounds) {
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
            int color=switch(ROWS[y].charAt(x)) {
                case 'a' -> 0xff2c73cc;
                case 'b' -> 0xff3790ff;
                case 'c' -> 0xff73b1ff;
                default -> 0;
            };
            if(color!=0) sink.fill(bounds.x()+x,bounds.y()+y,bounds.x()+x+1,bounds.y()+y+1,color);
        }
    }
}
