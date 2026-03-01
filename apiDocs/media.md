# Media Upload API

Provides a single generic endpoint for uploading images and videos to Catalyst FileStore. The returned URL is then attached to an entity (Exercise, MuscleGroup, Equipment, etc.) via that entity's existing update endpoint.

**Base path:** `/api/v1/media`
**Auth required:** Yes — all endpoints require authentication.

---

## How it works

```
1. POST /api/v1/media/upload   multipart/form-data  →  { url, fileId, fileName, mimeType, sizeBytes }
2. PUT  /api/v1/exercises/{id}                      →  { imageUrl: "<url from step 1>" }
   (or whichever entity update endpoint fits)
```

The server uploads the file to Catalyst FileStore under the named folder, constructs the permanent download URL, and returns it. The client is responsible for calling the entity update endpoint to attach the URL — the upload endpoint itself makes no changes to any DataStore record.

---

## FileStore folder structure

| Folder | Intended use |
|---|---|
| `exercises` | Exercise demo images and instructional videos |
| `routines` | Routine thumbnail images |
| `misc` | Any other media (default when no folder is specified) |

> **Setup required:** These three folders (`exercises`, `routines`, `misc`) must be created in the Catalyst Console under **FileStore** before the upload endpoint will work. Uploading to a non-existent folder returns 500.

---

## Endpoint

### POST /api/v1/media/upload

**Upload an image or video file.**

**Content-Type:** `multipart/form-data`

**Form fields**

| Field | Type | Required | Description |
|---|---|---|---|
| `file` | File | Yes | The image or video to upload. |
| `folder` | String | No | Target folder: `exercises` or `routines`. Any other value (or omitted) → `misc`. |

**Allowed file types**

| Category | MIME types | Max size |
|---|---|---|
| Image | `image/jpeg`, `image/png`, `image/webp` | 5 MB |
| Video | `video/mp4`, `video/quicktime` | 50 MB |

> The MIME type is read from the `Content-Type` header of the multipart part — not the file extension. Clients must set the part content type correctly.

**Example request**
```
POST /api/v1/media/upload?folder=exercises
Content-Type: multipart/form-data; boundary=----FormBoundary

------FormBoundary
Content-Disposition: form-data; name="file"; filename="bench-press.jpg"
Content-Type: image/jpeg

<binary image data>
------FormBoundary--
```

**Response — 201 Created**

```json
{
  "success": true,
  "data": {
    "url": "https://catalyst.zoho.com/baas/v1/project/12345/filestore/67/folder/9001/download",
    "fileId": 9001,
    "fileName": "bench-press.jpg",
    "mimeType": "image/jpeg",
    "sizeBytes": 245678
  }
}
```

| Field | Description |
|---|---|
| `url` | Permanent Catalyst FileStore download URL. Store this on the entity. |
| `fileId` | FileStore file ID. Retain if you need to delete the file later. |
| `fileName` | The original filename as stored in FileStore. |
| `mimeType` | MIME type echoed from the request part header. |
| `sizeBytes` | File size in bytes reported by FileStore after upload. |

**Errors**

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_REQUEST` | File is empty |
| 400 | `INVALID_REQUEST` | MIME type is not in the allowed list (e.g. PDF, GIF) |
| 400 | `INVALID_REQUEST` | File exceeds the per-type size limit (5 MB image / 50 MB video) |
| 413 | `FILE_TOO_LARGE` | File exceeds the Spring multipart limit (100 MB hard cap) |
| 500 | (generic) | FileStore folder doesn't exist or SDK upload failed |

---

## Linking the URL to an entity

After upload, call the entity's update endpoint with the URL:

**Exercise image:**
```
PUT /api/v1/exercises/{id}
{ "imageUrl": "https://catalyst.zoho.com/baas/v1/project/..." }
```

**Exercise video:**
```
PUT /api/v1/exercises/{id}
{ "videoUrl": "https://catalyst.zoho.com/baas/v1/project/..." }
```

> **Currently supported entities:** Only `PUT /api/v1/exercises/{id}` accepts `imageUrl` / `videoUrl`. Muscle group images and equipment images are not yet updatable via the REST API — they can be set at creation time (`POST /api/v1/exercises/categories` or `POST /api/v1/exercises/equipment`) but there is no update endpoint for those lookup tables.

---

## Downloading / displaying files

The FileStore download URL requires the user to be authenticated (Catalyst session or Bearer token). For mobile clients, include the same auth header used for all other API calls when fetching the image/video URL.

---

## Deleting files

There is currently no public API endpoint to delete a FileStore file. If you need to remove a file (e.g. when replacing an exercise image), it must be done manually via the Catalyst Console → FileStore, or added as a future endpoint.
