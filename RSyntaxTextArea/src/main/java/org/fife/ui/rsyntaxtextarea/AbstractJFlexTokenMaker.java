/*
 * 03/23/2005
 *
 * AbstractJFlexTokenMaker.java - Base class for token makers generated from
 * programs such as JFlex.
 *
 * This library is distributed under a modified BSD license.  See the included
 * LICENSE file for details.
 */
package org.fife.ui.rsyntaxtextarea;

import javax.swing.text.Segment;
import java.io.IOException;


/**
 * Base class for JFlex-generated token makers.  This class attempts to factor
 * out all common code from these classes.  Many methods <em>almost</em> could
 * be factored out into this class, but cannot because they reference JFlex
 * variables that we cannot access from this class.
 *
 * @author Robert Futrell
 * @version 0.1
 */
public abstract class AbstractJFlexTokenMaker extends TokenMakerBase {

	protected Segment s;

	protected int start;		// Just for states.
	protected int offsetShift;	// As parser always starts at 0, but our line doesn't.


	/**
	 * Declared here so we can define overloads that refer to this method.
	 *
	 * @param newState The new JFlex state to enter.
	 */
	public abstract void yybegin(int newState);


	/**
	 * Closes the input stream.  This method is defined for test purposes.
	 *
	 * @throws IOException If an IO error occurs.
	 */
	public abstract void yyclose() throws IOException;


	/**
	 * Starts a new JFlex state and changes the current language index.
	 *
	 * @param state The new JFlex state to enter.
	 * @param languageIndex The new language index.
	 */
	protected void yybegin(int state, int languageIndex) {
		yybegin(state);
		setLanguageIndex(languageIndex);
	}


	public abstract char yycharat(int pos);


	public abstract int yylength();


	public abstract void yypushback(int count);


	public abstract int yystate();


	public abstract String yytext();
}
