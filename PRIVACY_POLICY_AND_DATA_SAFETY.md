# Privacy Policy for PDF Master

**Effective Date:** November 6, 2026  
**Developer / Organization:** DarkSkullCorporation  
**Application Name:** PDF Master  
**Package Name:** `com.dsc.pdfmaster`  
**Contact Email:** darkskullcorporation@gmail.com  

---

## 1. Overview and Core Privacy Commitment

**PDF Master** is a privacy-first, 100% offline PDF utility developed by DarkSkullCorporation. Our core engineering principle is that **your documents belong to you**.

- **Zero Cloud Uploads:** All document processing (merging, splitting, compressing, viewing, organizing, converting, signing, OCR, and password protection) occurs entirely locally on your device.
- **Zero Network Transmission:** PDF Master does not declare or request the Android `INTERNET` permission. The application cannot communicate with any remote server, cloud storage provider, or third-party service.
- **No Analytics or Tracking SDKs:** We do not include any tracking SDKs, analytics frameworks, advertising networks, or third-party behavioral profiling tools.
- **No Account or Registration Required:** PDF Master does not require user accounts, email addresses, phone numbers, or passwords to operate.

---

## 2. Information Collection and Use

### 2.1 Document and File Data
- **What is accessed:** PDF documents, Office files (DOCX, XLSX, PPTX), images, and plain-text files that you explicitly choose to open or import using the Android Storage Access Framework (system file picker) or by sharing files into the app.
- **How it is processed:** Files are processed exclusively in your device's local memory (`RAM`) and sandboxed app storage.
- **Retention:** Output files are stored in your private application storage or saved directly to your chosen device folder when you export. Temporary processing files are automatically deleted upon completion or cancellation of the operation.
- **Cloud transfer:** Never. We have no servers, no databases in the cloud, and no network access.

### 2.2 Device and Technical Information
- The app does not collect, log, or transmit device identifiers, IMEI, Android ID, IP addresses, advertising IDs (AAID), or hardware specifications.

### 2.3 Password Protection and Credentials
- When you protect a PDF or remove a password from an existing PDF, the passwords you type are held ephemerally in device memory solely to configure the encryption/decryption engine.
- Passwords are **never stored in plaintext**, never written to disk or logs, and are cleared from memory as soon as the operation completes or the user navigates away.

---

## 3. Android System Permissions

PDF Master is designed with minimal permission footprint. The only runtime permission requested is:

| Permission | Purpose | Optional / Required |
|:---|:---|:---|
| `android.permission.POST_NOTIFICATIONS` | Used strictly on Android 13 (API 33)+ to display ongoing progress and completion notices for background document operations (e.g. batch compression or large file exports). | Optional. The app functions fully even if notifications are denied. |

PDF Master **does NOT request**:
- `android.permission.INTERNET` (No internet access)
- `android.permission.READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`
- `android.permission.MANAGE_EXTERNAL_STORAGE`
- `android.permission.CAMERA` (Document scanning utilizes Google Play Services or system Photo Picker without requiring direct camera access)
- `android.permission.RECORD_AUDIO`
- `android.permission.ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- `android.permission.READ_CONTACTS`

---

## 4. Google Play Console — Data Safety Form Declaration

When publishing on Google Play, use the following exact responses for the **Data Safety** questionnaire:

### Question 1: Data Collection and Security
- **Does your app collect or share any of the required user data types?**  
  👉 **No.** The app does not collect or share any user data.

- **Is all of the user data collected by your app encrypted in transit?**  
  👉 **Not applicable** (no data is collected or transmitted over any network).

- **Do you provide a way for users to request that their data be deleted?**  
  👉 **Yes.** Users have 100% control over their local documents and history. Users can delete any individual document or clear all history directly from within the application (Settings → Clear All History) or by uninstalling the application.

---

## 5. Security Practices

- **Local AES Encryption:** Support for PDF standard 128-bit and 256-bit AES encryption with distinct user and owner passwords.
- **Input Sanitization:** Filenames and paths are sanitized against path traversal (`..`), illegal filesystem characters, and overwrite collisions.
- **Sandboxed File Provider:** Document sharing uses Android `FileProvider` with temporary read-only URI grants.
- **Cloud Backup Exclusion:** Document caches, exported directories, and database tables are excluded from automated cloud backup to prevent orphaned or leaked data across devices.

---

## 6. Children’s Privacy

PDF Master does not target or knowingly collect data from children under the age of 13 (or under 16 in certain jurisdictions). Because no data is collected from any user, PDF Master complies with COPPA, GDPR, and Google Play Families Policy requirements.

---

## 7. Changes to This Privacy Policy

If we ever update this Privacy Policy, the revised version will be published at this location with an updated effective date.

---

## 8. Contact Us

If you have any questions or concerns regarding this Privacy Policy, please contact:  
**DarkSkullCorporation**  
Email: darkskullcorporation@gmail.com  
Repository: https://github.com/shahnaveed07/PDF_Master
