package diag;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.ParseException;

public class beehive {

	static boolean saveAttachments = false;
	static boolean showStructure = false;
	static int attnum = 1;
	static String indentStr = "                                               ";
	static int level = 0;

	public static void main(String[] args) {
		final Properties props = new Properties();
        //begin of main
		props.setProperty("mail.store.protocol", "imaps");
		props.setProperty("mail.imap.auth.plain.disable", "true");
		props.setProperty("mail.imaps.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		props.setProperty("mail.imaps.socketFactory.fallback", "true");

		try {
			Session session = javax.mail.Session.getInstance(props);
			session.setDebug(true);
			Store store = session.getStore();
			store.connect("stbeehive.oracle.com", 993, "vijaya.gopisetty_appr@oracle.com", "Image.123");

			Folder folder = store.getFolder("Inbox");
			if (folder == null) {
				System.out.println("Invalid folder");
				System.exit(1);
			}
			System.out.println(folder.getName());

			try {
				folder.open(Folder.READ_WRITE);
			} catch (MessagingException ex) {
				folder.open(Folder.READ_ONLY);
			}
			Message[] msgs = folder.getMessages();
			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			fp.add(FetchProfile.Item.FLAGS);
			fp.add("X-Mailer");
			folder.fetch(msgs, fp);

			for (int i = 0; i < msgs.length; i++) {
				System.out.println("--------------------------");
				System.out.println("MESSAGE #" + (i + 1) + ":");
				dumpEnvelope(msgs[i]);
				if (saveAttachments) { 
				  dumpPart(msgs[i]);
				}
			}

			store.close();
		} catch (MessagingException ex) {
			// Logger.getLogger(JavaMailTest.class.getName()).log(Level.SEVERE,
			// null, ex);
			System.out.println(ex.getMessage());
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
	}
	
	public static void dumpEnvelope(Message m) throws Exception {
		pr("This is the message envelope");
		pr("---------------------------");
		Address[] a;
		// FROM
		if ((a = m.getFrom()) != null) {
			for (int j = 0; j < a.length; j++)
				pr("FROM: " + a[j].toString());
		}

		// REPLY TO
		if ((a = m.getReplyTo()) != null) {
			for (int j = 0; j < a.length; j++)
				pr("REPLY TO: " + a[j].toString());
		}

		// TO
		if ((a = m.getRecipients(Message.RecipientType.TO)) != null) {
			for (int j = 0; j < a.length; j++) {
				pr("TO: " + a[j].toString());
				InternetAddress ia = (InternetAddress) a[j];
				if (ia.isGroup()) {
					InternetAddress[] aa = ia.getGroup(false);
					for (int k = 0; k < aa.length; k++)
						pr("  GROUP: " + aa[k].toString());
				}
			}
		}

		// SUBJECT
		pr("SUBJECT: " + m.getSubject());

		// DATE
		Date d = m.getSentDate();
		pr("SendDate: " + (d != null ? d.toString() : "UNKNOWN"));

		if (m.getSubject().contains("Action Required")) {
			saveAttachments = true;
		} else
		{
			saveAttachments = false;
		}
	}
	

	public static void dumpPart(Part p) throws Exception {
		if (p instanceof Message)
			dumpEnvelope((Message) p);

		/**
		 * Dump input stream ..
		 * 
		 * InputStream is = p.getInputStream(); // If "is" is not already
		 * buffered, wrap a BufferedInputStream // around it. if (!(is
		 * instanceof BufferedInputStream)) is = new BufferedInputStream(is);
		 * int c; while ((c = is.read()) != -1) System.out.write(c);
		 * 
		 **/

		String ct = p.getContentType();
		try {
			pr("CONTENT-TYPE: " + (new ContentType(ct)).toString());
		} catch (ParseException pex) {
			pr("BAD CONTENT-TYPE: " + ct);
		}
		String filename = p.getFileName();
		if (filename != null)
			pr("FILENAME: " + filename);

		/*
		 * Using isMimeType to determine the content type avoids fetching the
		 * actual content data until we need it.
		 */
		if (p.isMimeType("text/plain")) {
			pr("This is plain text");
			pr("---------------------------");
			if (!showStructure && !saveAttachments)
				System.out.println((String) p.getContent());
		} else if (p.isMimeType("multipart/*")) {
			pr("This is a Multipart");
			pr("---------------------------");
			Multipart mp = (Multipart) p.getContent();
			level++;
			int count = mp.getCount();
			for (int i = 0; i < count; i++)
				dumpPart(mp.getBodyPart(i));
			level--;
		} else if (p.isMimeType("message/rfc822")) {
			pr("This is a Nested Message");
			pr("---------------------------");
			level++;
			dumpPart((Part) p.getContent());
			level--;
		} else {
			if (!showStructure && !saveAttachments) {
				/*
				 * If we actually want to see the data, and it's not a MIME type
				 * we know, fetch it and check its Java type.
				 */
				Object o = p.getContent();
				if (o instanceof String) {
					pr("This is a string");
					pr("---------------------------");
					System.out.println((String) o);
				} else if (o instanceof InputStream) {
					pr("This is just an input stream");
					pr("---------------------------");
					InputStream is = (InputStream) o;
					int c;
					while ((c = is.read()) != -1)
						System.out.write(c);
				} else {
					pr("This is an unknown type");
					pr("---------------------------");
					pr(o.toString());
				}
			} else {
				// just a separator
				pr("---------------------------");
			}
		}

		/*
		 * If we're saving attachments, write out anything that looks like an
		 * attachment into an appropriately named file. Don't overwrite existing
		 * files to prevent mistakes.
		 */
		if (saveAttachments && level != 0 && p instanceof MimeBodyPart && !p.isMimeType("multipart/*")) {
			String disp = p.getDisposition();
			// many mailers don't include a Content-Disposition
			if (disp == null || disp.equalsIgnoreCase(Part.ATTACHMENT)) {
				if (filename == null)
					filename = "Attachment" + attnum++;
				pr("Saving attachment to file " + filename);
				try {
					File f = new File(filename);
					if (f.exists())
						// XXX - could try a series of names
						throw new IOException("file exists");
					((MimeBodyPart) p).saveFile(f);
				} catch (IOException ex) {
					pr("Failed to save attachment: " + ex);
				}
				pr("---------------------------");
			}
		}
	}


	/**
	 * Print a, possibly indented, string.
	 */
	public static void pr(String s) {
		if (showStructure)
			System.out.print(indentStr.substring(0, level * 2));
		System.out.println(s);
	}

}
