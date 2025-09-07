/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mcworldinspector.utils;

import java.util.OptionalInt;

/**
 *
 * @author matthias
 */
public class StringHelpers {
    
    private StringHelpers() {}
    
    public static String formatCount(long count, String unit) {
        if (count == 1)
            return count + " " + unit;
        return count + " " + unit + "s";
    }
    
    public static String formatCount(long count, String unit, int width) {
        return String.format("%" + width + "d %s%s", count, unit, (count == 1) ? "" : "s");
    }
    
    public static int widthForCount(long count) {
        return Long.toString(count).length();
    }
    
    public static int widthForCount(OptionalInt count) {
        if (count.isPresent())
            return widthForCount(count.getAsInt());
        return 1;
    }
}
