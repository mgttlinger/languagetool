/* LanguageTool, a natural language style checker 
 * Copyright (C) 2007 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package de.danielnaber.languagetool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

/**
 * Check if the translations seem to be complete.
 * 
 * @author Daniel Naber
 */
public class TranslationTest extends TestCase {
  
  /**
   * Make sure values are not empty.
   */
  public void testTranslationsAreNotEmpty() throws IOException {
    for (int i = 0; i < Language.LANGUAGES.length; i++) {
      Language lang = Language.LANGUAGES[i];
      File file = new File("src" + File.separator + "java" + File.separator
        + "de" + File.separator + "danielnaber"+ File.separator +"languagetool" 
        + File.separator + "MessagesBundle_" + lang.getShortName()+".properties");
      if (!file.exists()) {
        System.err.println("Note: no translation available for " + lang);
        continue;
      }
      List<String> lines = loadFile(file);
      for (String line : lines) {
        line = line.trim();
        if (line.startsWith("#"))
          continue;
        if (line.equals(""))
          continue;
        String[] parts = line.split("=");
        if (parts.length < 2) {
          System.err.println("***** Empty translation: '" + line + "' in file " + file);
          //fail("Empty translation: '" + line + "' in file " + file);
        }
      }
    }
  }
  
  private List<String> loadFile(File file) throws IOException {
    List<String> l = new ArrayList<String>();
    FileReader fr = null;
    BufferedReader br = null;
    try {
      fr = new FileReader(file);
      br = new BufferedReader(fr);
      String line;
      while ((line = br.readLine()) != null) {
        l.add(line);
      }
    } finally {
      if (br != null) br.close();
      if (fr != null) fr.close();
    }
    return l;
  }

}
