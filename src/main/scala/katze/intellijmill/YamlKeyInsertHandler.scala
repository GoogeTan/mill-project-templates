package katze.intellijmill

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement

object YamlKeyInsertHandler extends InsertHandler[LookupElement]:
  override def handleInsert(context: InsertionContext, item: LookupElement): Unit =
    val editor = context.getEditor
    val document = context.getDocument

    // getTailOffset() gives the position right after the newly inserted word
    val offset = context.getTailOffset
    val chars = document.getCharsSequence

    // Look ahead to see if the user already typed a colon (ignoring spaces)
    var currentOffset = offset
    while currentOffset < chars.length() && chars.charAt(currentOffset) == ' ' do
      currentOffset += 1

    val hasColon = currentOffset < chars.length() && chars.charAt(currentOffset) == ':'

    // Only insert if the colon doesn't already exist
    if !hasColon then
      document.insertString(offset, ": ")
      // Move the cursor after the newly inserted colon and space
      editor.getCaretModel.moveToOffset(offset + 2)
end YamlKeyInsertHandler
