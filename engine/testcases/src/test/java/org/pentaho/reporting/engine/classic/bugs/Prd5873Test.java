/*
 * This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 *  Foundation.
 *
 *  You should have received a copy of the GNU Lesser General Public License along with this
 *  program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  or from the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  Copyright (c) 2006 - 2017 Hitachi Vantara..  All rights reserved.
 */

package org.pentaho.reporting.engine.classic.bugs;

import com.lowagie.text.pdf.PRTokeniser;
import com.lowagie.text.pdf.PdfContentParser;
import com.lowagie.text.pdf.PdfLiteral;
import com.lowagie.text.pdf.PdfObject;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfContentReaderTool;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.reporting.engine.classic.core.ClassicEngineBoot;
import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.ReportProcessingException;
import org.pentaho.reporting.engine.classic.core.modules.output.pageable.pdf.PdfReportUtil;
import org.pentaho.reporting.engine.classic.core.style.TextStyleKeys;
import org.pentaho.reporting.libraries.resourceloader.ResourceException;
import org.pentaho.reporting.libraries.resourceloader.ResourceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Stack;

/**
 * @author Thomas Morgner
 */
public class Prd5873Test {

    @Before
    public void setUp() throws Exception {
        ClassicEngineBoot.getInstance().start();
    }

    @Test
    public void validateWithUnderline()  throws ResourceException, ReportProcessingException, IOException {
        final URL resource = getClass().getResource("Prd-5873.prpt");
        final MasterReport report = (MasterReport)
                new ResourceManager().createDirectly(resource, MasterReport.class).getResource();
        report.getPageFooter().getElement(0).getStyle().setBooleanStyleProperty(TextStyleKeys.UNDERLINED, false);
        runTest(report);
    }

    @Test
    public void validateCorrectness()
            throws ResourceException, ReportProcessingException, IOException {
        final URL resource = getClass().getResource("Prd-5873.prpt");
        final MasterReport report = (MasterReport)
                new ResourceManager().createDirectly(resource, MasterReport.class).getResource();
        runTest(report);
    }

    private void runTest(MasterReport report)
            throws ResourceException, ReportProcessingException, IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PdfReportUtil.createPDF(report, bout);

        PdfReader reader = new PdfReader(bout.toByteArray());

        printPdfPage(reader);

        validate(reader.getPageContent(1));
    }

    private void printPdfPage(PdfReader reader) throws IOException {
        final StringWriter out = new StringWriter();
        PdfContentReaderTool.listContentStreamForPage(reader, 1, new PrintWriter(out));
        System.out.println(out);
    }


    /**
     * The page content must not start or end a graphics state (q/Q) inside a text object (BT/ET), and every q
     * needs its Q. (OpenPDF has no PdfContentStreamProcessor, so the operators are read with PdfContentParser.)
     */
    private void validate(byte[] pageContent) throws IOException {
        final Stack<Boolean> graphicsState = new Stack<>();
        boolean textModeActive = false;
        final PdfContentParser parser = new PdfContentParser(new PRTokeniser(pageContent));
        final ArrayList<PdfObject> operands = new ArrayList<>();
        while (parser.parse(operands).size() > 0) {
            final String op = ((PdfLiteral) operands.get(operands.size() - 1)).toString();
            if (Objects.equals(op, "q") || Objects.equals(op, "Q")) {
                if (textModeActive) {
                    throw new RuntimeException("Cannot mix text mode and graphics operations.");
                }
                if (op.equals("q")) {
                    graphicsState.push(Boolean.TRUE);
                } else {
                    graphicsState.pop();
                }
            } else if (Objects.equals(op, "BT")) {
                textModeActive = true;
            } else if (Objects.equals(op, "ET")) {
                textModeActive = false;
            }
        }
        if (!graphicsState.isEmpty()) {
            throw new RuntimeException("Unbalanced graphics state (q without Q).");
        }
    }
}
