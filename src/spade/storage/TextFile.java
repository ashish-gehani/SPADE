/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.storage;

import org.apache.commons.codec.digest.DigestUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;

import java.io.FileWriter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A storage implementation that simply outputs plain text to a file.
 * @author Armando Caro
 */
public class TextFile extends AbstractStorage {

    private FileWriter outputFile;
    private String filePath;

    @Override
    public boolean initialize(String arguments) {
        try {
            if (arguments == null) {
                return false;
            }
            filePath = arguments;
            outputFile = new FileWriter(filePath, false);
            outputFile.write("[BEGIN]\n");
            return true;
        } catch (Exception exception) {
            Logger.getLogger(TextFile.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        try {
            String vertexId = DigestUtils.sha256Hex(incomingVertex.toString());
            StringBuilder annotationString = new StringBuilder();
            annotationString.append("VERTEX (" + vertexId + "): {");
            for (Map.Entry<String, String> currentEntry : incomingVertex.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                annotationString.append(key);
                annotationString.append(":");
                annotationString.append(value);
                annotationString.append(",");
            }
            annotationString.append("}\n");
            String vertexString = annotationString.toString();
            outputFile.write(vertexString);
            return true;
        } catch (Exception exception) {
            Logger.getLogger(TextFile.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        try {
            String srcId = DigestUtils.sha256Hex(incomingEdge.getSourceVertex().toString());
            String dstId = DigestUtils.sha256Hex(incomingEdge.getDestinationVertex().toString());
            StringBuilder annotationString = new StringBuilder();
            annotationString.append("EDGE (" + srcId + " -> " + dstId + "): {");
            for (Map.Entry<String, String> currentEntry : incomingEdge.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                annotationString.append(key);
                annotationString.append(":");
                annotationString.append(value);
                annotationString.append(",");
            }
            annotationString.append("}\n");
            String edgeString = annotationString.toString();
            outputFile.write(edgeString);
            return true;
        } catch (Exception exception) {
            Logger.getLogger(TextFile.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        try {
            outputFile.write("[END]\n");
            outputFile.close();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(TextFile.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }
}
