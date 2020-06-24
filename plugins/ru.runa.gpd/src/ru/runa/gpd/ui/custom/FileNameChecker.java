package ru.runa.gpd.ui.custom;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;

//
// Based on org.eclipse.core.internal.resources.OS
//
public abstract class FileNameChecker extends KeyAdapter {

    private static final List<Character> forbiddenCharacters = Lists.newArrayList('\\', '/', ':', '*', '?', '"', '<', '>', '|', '%', '!', '@', '\0');
    private static final String[] forbiddenBaseNames = new String[] { "aux", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "con", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "nul", "prn" };
    private static final String[] forbiddenFullNames = new String[] { "clock$" };

    @Override
    public void keyPressed(KeyEvent e) {
        if (forbiddenCharacters.contains(e.character)) {
            e.doit = false;
        }
    }

    public static boolean isValid(String fileName) {
        if (fileName == null) {
            return false;
        } else {
            final int length = fileName.length();
            if (length == 0) {
                return false;
            } else {
                final char lastChar = fileName.charAt(length - 1);
                if (lastChar == '.' || Character.isWhitespace(lastChar) || fileName.startsWith(" ") || fileName.contains("  ")) {
                    return false;
                }
            }
            for (char c : fileName.toCharArray()) {
                if (forbiddenCharacters.contains(c)) {
                    return false;
                }
            }
            final int dot = fileName.indexOf('.');
            // on windows, filename suffixes are not relevant to name validity
            String basename = dot < 0 ? fileName : fileName.substring(0, dot);
            if (Arrays.binarySearch(forbiddenBaseNames, basename.toLowerCase()) >= 0) {
                return false;
            }
            return Arrays.binarySearch(forbiddenFullNames, fileName.toLowerCase()) < 0;
        }
    }
    
    public static boolean firstSymbolIsDot(String fileName) {
    	if (fileName == null) {
    		return false;
    	} else {
    		final int length = fileName.length();
    		if (length == 0) {
    			return false;
    		} else {
    			final char firstChar = fileName.charAt(0);
    			if (firstChar == '.') {
    				return true;
    			}
    		}
    	}
    	return false;
    }
    
}
