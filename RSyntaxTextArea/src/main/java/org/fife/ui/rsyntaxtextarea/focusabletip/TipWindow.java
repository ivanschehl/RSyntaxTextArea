/*
 * 07/29/2009
 *
 * TipWindow.java - The actual window component representing the tool tip.
 *
 * This library is distributed under a modified BSD license.  See the included
 * LICENSE file for details.
 */
package org.fife.ui.rsyntaxtextarea.focusabletip;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.MouseInputAdapter;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;

import org.fife.ui.rsyntaxtextarea.HtmlUtil;


/**
 * The actual tool tip component.
 *
 * @author Robert Futrell
 * @version 1.0
 */
class TipWindow extends JWindow {

	private FocusableTip ft;
	private JEditorPane textArea;
	private transient TipListener tipListener;
	private transient HyperlinkListener userHyperlinkListener;

	private static TipWindow visibleInstance;

	private static final String FLAT_LAF_BORDER_PREFIX = "com.formdev.flatlaf.ui.Flat";


	/**
	 * Constructor.
	 *
	 * @param owner The parent window.
	 * @param msg The text of the tool tip.  This can be HTML.
	 */
	TipWindow(Window owner, FocusableTip ft, String msg) {

		super(owner);
		this.ft = ft;
		// Render plain text tool tips correctly.
		if (msg!=null && msg.length()>=6 &&
				!msg.substring(0,6).equalsIgnoreCase("<html>")) {
			msg = "<html>" + HtmlUtil.escapeForHtml(msg, "<br>", false);
		}
		tipListener = new TipListener();

		JPanel cp = new JPanel(new BorderLayout());
		cp.setBorder(getToolTipBorder());
		cp.setBackground(TipUtil.getToolTipBackground());
		textArea = new JEditorPane("text/html", msg);
		TipUtil.tweakTipEditorPane(textArea);
		if (ft.getImageBase()!=null) { // Base URL for images
			((HTMLDocument)textArea.getDocument()).setBase(ft.getImageBase());
		}
		textArea.addMouseListener(tipListener);
		textArea.addHyperlinkListener(e -> {
			if (e.getEventType()==HyperlinkEvent.EventType.ACTIVATED) {
				TipWindow.this.ft.possiblyDisposeOfTipWindow();
			}
		});
		cp.add(textArea);

		setFocusableWindowState(false);
		setContentPane(cp);
		setBottomPanel(); // Must do after setContentPane()
		pack();

		// InputMap/ActionMap combo doesn't work for JWindows (even when
		// using the JWindow's JRootPane), so we'll resort to KeyListener
		KeyAdapter ka = new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode()==KeyEvent.VK_ESCAPE) {
					TipWindow.this.ft.possiblyDisposeOfTipWindow();
				}
			}
		};
		addKeyListener(ka);
		textArea.addKeyListener(ka);

		// Ensure only 1 TipWindow is ever visible.  If the caller does what
		// they're supposed to and only creates these on the EDT, the
		// synchronization isn't necessary, but we'll be extra safe.
		synchronized (TipWindow.class) {
			if (visibleInstance!=null) {
				visibleInstance.dispose();
			}
			visibleInstance = this;
		}

	}


	public void actionPerformed() {
		if (!getFocusableWindowState()) {
			setFocusableWindowState(true);
			setBottomPanel();
			textArea.removeMouseListener(tipListener);
			pack();
			addWindowFocusListener(new WindowAdapter() {
				@Override
				public void windowLostFocus(WindowEvent e) {
					ft.possiblyDisposeOfTipWindow();
				}
			});
			ft.removeListeners();
			requestFocus();
		}
	}


	/**
	 * Disposes of this window.
	 */
	@Override
	public void dispose() {
		//System.out.println("[DEBUG]: Disposing...");
		Container cp = getContentPane();
		for (int i=0; i<cp.getComponentCount(); i++) {
			// Okay if listener is already removed
			cp.getComponent(i).removeMouseListener(tipListener);
		}
		ft.removeListeners();
		super.dispose();
	}


	/**
	 * Workaround for JEditorPane not returning its proper preferred size
	 * when rendering HTML until after layout already done.
	 */
	void fixSize() {

		Dimension d;
		Rectangle r;
		try {

			// modelToView call is required for this hack, never remove!
			r = textArea.modelToView(textArea.getDocument().getLength()-1);

			// Ensure the text area doesn't start out too tall or wide.
			d = textArea.getPreferredSize();
			d.width += 25; // Just a little extra space
			final int maxWindowW = ft.getMaxSize() != null ?
					ft.getMaxSize().width : 600;
			final int maxWindowH = ft.getMaxSize() != null ?
					ft.getMaxSize().height : 400;
			d.width = Math.min(d.width, maxWindowW);
			d.height = Math.min(d.height, maxWindowH);

			// Both needed for modelToView() calculation below...
			textArea.setPreferredSize(d);
			textArea.setSize(d);

			// if the new textArea width causes our text to wrap, we must
			// compute a new preferred size to get all our physical lines.
			r = textArea.modelToView(textArea.getDocument().getLength()-1);
			if (r.y+r.height>d.height) {
				d.height = r.y + r.height + 5;
				if(ft.getMaxSize() != null) {
					d.height = Math.min(d.height, maxWindowH);
				}
				textArea.setPreferredSize(d);
			}

		} catch (BadLocationException ble) { // Never happens
			ble.printStackTrace();
		}

		pack(); // Must re-pack to calculate proper size.

	}


	/**
	 * FlatLaf adds insets to tool tips, and for some themes (usually light ones)
	 * also uses a line border, whereas for other themes (usually dark ones)
	 * there is no line border.  We need to ensure our border has no insets
	 * so our draggable bottom component looks good, but we'd like to preserve
	 * the color of the line border, if any.  This method allows us to do so
	 * without a compile-time dependency on flatlaf.
	 *
	 * @param border The default tool tip border for the current Look and Feel.
	 * @return The border to use for this window.
	 */
	private static Border getReplacementForFlatLafBorder(Border border) {

		Class<?> clazz = border.getClass();

		// If it's a FlatLineBorder, get its color.
		// If it's a FlatEmptyBorder, just return a 0-sized regular EmptyBorder.
		Color color = null;
		Method[] methods = clazz.getDeclaredMethods();
		for (Method method : methods) {
			if ("getLineColor".equals(method.getName())) {
				try {
					color = (Color)method.invoke(border);
				} catch (IllegalAccessException | InvocationTargetException e) {
					e.printStackTrace(); // Never happens
				}
			}
		}

		if (color != null) {
			return BorderFactory.createLineBorder(color);
		}
		return BorderFactory.createEmptyBorder();
	}


	protected String getText() {
		return textArea.getText();
	}


	private static Border getToolTipBorder() {

		Border border = TipUtil.getToolTipBorder();


		// Special case for FlatDarkLaf and FlatLightLaf, since they add an
		// empty border to tool tips that messes up our floating-window appearance
		if (isFlatLafBorder(border)) {
			border = getReplacementForFlatLafBorder(border);
		}

		return border;
	}


	private static boolean isFlatLafBorder(Border border) {
		return border != null && border.getClass().getName().startsWith(FLAT_LAF_BORDER_PREFIX);
	}


	private void setBottomPanel() {

		final JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JSeparator(), BorderLayout.NORTH);

		boolean focusable = getFocusableWindowState();
		if (focusable) {
			SizeGrip sg = new SizeGrip();
			sg.applyComponentOrientation(sg.getComponentOrientation()); // Workaround
			panel.add(sg, BorderLayout.LINE_END);
			MouseInputAdapter adapter = new MouseInputAdapter() {
				private Point lastPoint;
				@Override
				public void mouseDragged(MouseEvent e) {
					Point p = e.getPoint();
					SwingUtilities.convertPointToScreen(p, panel);
					if (lastPoint != null) {
						int dx = p.x - lastPoint.x;
						int dy = p.y - lastPoint.y;
						setLocation(getX() + dx, getY() + dy);
					}
					lastPoint = p;
				}
				@Override
				public void mousePressed(MouseEvent e) {
					lastPoint = e.getPoint();
					SwingUtilities.convertPointToScreen(lastPoint, panel);
				}
			};
			panel.addMouseListener(adapter);
			panel.addMouseMotionListener(adapter);
			// Don't add tipListener to the panel or SizeGrip
		}
		else {
			panel.setOpaque(false);
			JLabel label = new JLabel(FocusableTip.getString("FocusHotkey"));
			Color fg = UIManager.getColor("Label.disabledForeground");
			Font font = textArea.getFont();
			font = font.deriveFont(font.getSize2D() - 1.0f);
			label.setFont(font);
			if (fg==null) { // Non BasicLookAndFeel-derived Looks
				fg = Color.GRAY;
			}
			label.setOpaque(true);
			Color bg = TipUtil.getToolTipBackground();
			label.setBackground(bg);
			label.setForeground(fg);
			label.setHorizontalAlignment(SwingConstants.TRAILING);
			label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
			panel.add(label);
			panel.addMouseListener(tipListener);
		}

		// Replace the previous SOUTH Component with the new one.
		Container cp = getContentPane();
		if (cp.getComponentCount()==2) { // Skip first time through
			Component comp = cp.getComponent(0);
			cp.remove(0);
			JScrollPane sp = new JScrollPane(comp);
			Border emptyBorder = BorderFactory.createEmptyBorder();
			sp.setBorder(emptyBorder);
			sp.setViewportBorder(emptyBorder);
			sp.setBackground(textArea.getBackground());
			sp.getViewport().setBackground(textArea.getBackground());
			cp.add(sp);
			// What was component 1 is now 0.
			cp.getComponent(0).removeMouseListener(tipListener);
			cp.remove(0);
		}

		cp.add(panel, BorderLayout.SOUTH);

	}


	/**
	 * Sets the listener for hyperlink events in this tip window.
	 *
	 * @param listener The new listener.  The old listener (if any) is
	 *        removed.  A value of <code>null</code> means "no listener."
	 */
	public void setHyperlinkListener(HyperlinkListener listener) {
		// We've added a separate listener, so remove only the user's.
		if (userHyperlinkListener!=null) {
			textArea.removeHyperlinkListener(userHyperlinkListener);
		}
		userHyperlinkListener = listener;
		if (userHyperlinkListener!=null) {
			textArea.addHyperlinkListener(userHyperlinkListener);
		}
	}


	/**
	 * Listens for events in this window.
	 */
	private final class TipListener extends MouseAdapter {

		private TipListener() {
		}

		@Override
		public void mousePressed(MouseEvent e) {
			actionPerformed(); // Manually create "real" window
		}

		@Override
		public void mouseExited(MouseEvent e) {
			// Since we registered this listener on the child components of
			// the JWindow, not the JWindow itself, we have to be careful.
			Component source = (Component)e.getSource();
			Point p = e.getPoint();
			SwingUtilities.convertPointToScreen(p, source);
			if (!TipWindow.this.getBounds().contains(p)) {
				ft.possiblyDisposeOfTipWindow();
			}
		}

	}


}
