package com.yassine.facturemaroc.data

/**
 * What happened when a document was written as XML.
 *
 * [Saved] means the file really reached the place the user picked. It is
 * separate from the PDF share outcome because SAF reports a different failure:
 * the user can cancel the picker, the provider can refuse the write, or there
 * can be nothing worth exporting.
 */
sealed interface XmlExportOutcome {
    data object Saved : XmlExportOutcome
    data object Failed : XmlExportOutcome
    data object NothingToExport : XmlExportOutcome
}
