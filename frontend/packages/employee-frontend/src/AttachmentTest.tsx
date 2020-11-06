import React, { ChangeEvent, useState } from 'react'
import { client } from '~api/client'

export default React.memo(function AttachmentTest() {
  const [file, setFile] = useState<File | null>(null)

  const onFileSelect = (e: ChangeEvent<HTMLInputElement>) => {
    setFile(e.target.files?.item(0) ?? null)
  }

  const onFileUpload = () => {
    if (!file) return

    const formData = new FormData()
    formData.append('file', file)

    client
      .post('attachments', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        },
        onUploadProgress: (progressEvent: ProgressEvent) => {
          if (progressEvent.lengthComputable) {
            console.log(
              `${(100.0 * progressEvent.loaded) / progressEvent.total}%`
            )
          }
        }
      })
      .then((data) => {
        console.log(data)
      })
      .catch(() => {
        console.log('fail')
      })
  }

  return (
    <div>
      <input
        type="file"
        accept=".pdf,application/pdf,.jpg,.jpeg,image/jpeg,.png,image/png,.doc,application/msword,.docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        onChange={onFileSelect}
      />
      {file && <button onClick={onFileUpload}>upload</button>}
    </div>
  )
})
