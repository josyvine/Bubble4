package com.app.bubble;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

public class EmojiUtils {

    // A curated list of popular modern Unicode Emojis
    public static final String[] EMOJIS = {
        // Smileys & People
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
        "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏",
        "😒", "😞", "😔", "wworried", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠",
        "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "YW", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫",
        "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵",
        "🤐", "🥴", "🤢", "🤮", "sneezing", "😷", "xk", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "hiding", "👻",
        "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾",
        "👋", "🤚", "qm", "✋", "🖖", "👌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕",
        "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
        "🤳", "💪", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🦷", "bone", "👀", "👁", "👅", "👄", "💋", "🩸",

        // Animals & Nature
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "cow", "🐷", "🐽", "🐸", "🐵",
        "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "duck", "eagle", "owl", "bat", "wolf",
        "boar", "horse", "unicorn", "bee", "bug", "butterfly", "snail", "beetle", "ant", "mosquito", "cricket",
        "spider", "web", "turtle", "snake", "lizard", "t-rex", "octopus", "squid", "shrimp", "lobster", "crab",
        "fish", "dolphin", "whale", "shark", "crocodile", "tiger", "leopard", "zebra", "gorilla", "orangutan",
        "elephant", "hippo", "rhino", "camel", "giraffe", "kangaroo", "buffalo", "bull", "cow2", "pig", "ram",
        "sheep", "llama", "goat", "deer", "dog2", "poodle", "cat2", "rooster", "turkey", "peacock", "parrot",
        "swan", "flamingo", "rabbit", "raccoon", "skunk", "badger", "otter", "sloth", "mouse", "rat", "chipmunk",
        "hedgehog", "cactus", "tree", "pine", "deciduous", "palm", "seedling", "herb", "shamrock", "clover",
        "bamboo", "tanabata", "leaf", "fallen", "maple", "mushroom", "shell", "rose", "wilted", "hibiscus",
        "cherry", "blossom", "flower", "sunflower", "daisy", "tulip", "seed", "trunk", "sun", "moon", "star",
        "fire", "water", "cloud", "rain", "lightning", "snow", "rainbow", "umbrella", "zap", "ocean",

        // Objects & Hearts
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
        "💘", "💝", "💯", "💢", "💥", "💫", "💦", "💨", "🕳", "💣", "💬", "👁️‍🗨️", "🗨", "🗯", "💭", "💤",
        "💡", "🔦", "🕯", "🪔", "📔", "📕", "📖", "📗", "📘", "📙", "📚", "📓", "📒", "📃", "📜", "📄",
        "📰", "🗞", "📑", "🔖", "🏷", "💰", "💴", "💵", "💶", "💷", "💸", "💳", "🧾", "💹", "✉️", "📧",
        "📨", "📩", "📤", "📥", "📦", "📫", "📪", "📬", "📭", "📮", "🗳", "✏️", "✒️", "🖋", "🖊", "🖌",
        "🖍", "📝", "💼", "📁", "📂", "🗂", "📅", "📆", "🗒", "🗓", "📇", "📈", "📉", "📊", "📋", "📌",
        "📍", "📎", "🖇", "📏", "📐", "✂️", "🗃", "🗄", "🗑", "🔒", "🔓", "🔏", "🔐", "🔑", "🗝", "🔨",
        "🪓", "⛏", "⚒", "🛠", "dagger", "⚔️", "gun", "boomerang", "bow", "shield", "wrench", "nut", "gear",
        "clamp", "balance", "link", "chains", "hook", "toolbox", "magnet", "ladder", "⚗️", "🧪", "🧫", "🧬",
        "🔬", "🔭", "📡", "syringe", "drop", "pill", "bandaid", "steth", "door", "chair", "toilet", "shower",
        "bath", "razor", "lotion", "pin", "broom", "basket", "roll", "soap", "sponge", "extinguisher", "cart"
    };

    /**
     * Interface to handle emoji clicks in the Service
     */
    public interface EmojiListener {
        void onEmojiClick(String emoji);
    }

    /**
     * Sets up the Emoji GridView with the adapter and click listeners.
     * 
     * @param context The application context
     * @param rootView The root view of the emoji palette layout
     * @param listener The callback to handle emoji selection
     */
    public static void setupEmojiGrid(final Context context, View rootView, final EmojiListener listener) {
        GridView grid = rootView.findViewById(R.id.emoji_grid);
        
        // Basic Buttons in the layout (logic can be extended for tabs)
        Button btnSmileys = rootView.findViewById(R.id.tab_smileys);
        Button btnAnimals = rootView.findViewById(R.id.tab_animals);
        // Note: Real category filtering would require separate lists. 
        // For this version, we show the mega-list.
        
        // Create the Adapter
        // We use a custom getView logic inside a standard ArrayAdapter to ensure size/centering
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, EMOJIS) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // Use the standard text view but customize it slightly
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextSize(28); // Make emojis large
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setTextColor(Color.BLACK); // Ensure visibility
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setPadding(0, 10, 0, 10);
                return tv;
            }
        };

        grid.setAdapter(adapter);

        // Handle Click
        grid.setOnItemClickListener((parent, view, position, id) -> {
            String selectedEmoji = EMOJIS[position];
            // Only send if it's a valid string (filtering out placeholders if any)
            if (selectedEmoji != null && !selectedEmoji.equals("xk")) { 
                listener.onEmojiClick(selectedEmoji);
            }
        });
    }
}