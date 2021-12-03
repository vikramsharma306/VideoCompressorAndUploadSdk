package com.multitv.ott.videocompressoranduploadsdk

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import kotlinx.android.synthetic.main.activity_video_sdk_compress.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.*
import kotlin.math.log10
import kotlin.math.pow

class VideoCompressAndVideoUploadHelper(private val context: Context, private val onVideoCompressAndUploadListener: onVideoCompressAndUploadListener) {
    private lateinit var playableVideoPath: String
    private lateinit var videoUrl: String

    fun videoCompressAndUploadSdkInit(uri: Uri, isCamera: Boolean) {
        uploadAndCompressVideo(uri, isCamera)
    }

    private fun uploadAndCompressVideo(uri: Uri, isCamera: Boolean) {
        var pathOfVideo = ""
        var streamableFile: File? = null

        var originalFileSize = File(getMediaPath(context, uri!!)).length()
        val digitGroups = (log10(originalFileSize.toDouble()) / log10(1024.0)).toInt()
        val totalSize = originalFileSize / 1024.0.pow(digitGroups.toDouble())


        // getting Duration from MediaMetadataRetriever
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val millSecond = durationStr!!.toInt()
        val minutes = millSecond / 1000 / 60
        val seconds = (millSecond / 1000 % 60)
        Log.e("File Duration:::", "Less :::" + minutes)
        Log.e("File length:::", "original :::" + totalSize)

        GlobalScope.launch {
            val job = async { getMediaPath(context, uri) }
            val path = job.await()

            val desFile = saveVideoFile(uri.path)
            streamableFile = saveVideoFile(uri.path)

            playableVideoPath = if (streamableFile != null) streamableFile!!.path
            else path

            Log.e("File length:::", "desv File :::" + desFile?.length())
            Log.e("File length:::", "streamable File :::" + streamableFile?.length())



            if (isCamera) {
                if (totalSize < 50 || seconds < 30) {
                    uploadVideoOnAws(pathOfVideo)
                }
            } else {

            }


        }

    }


    @Suppress("DEPRECATION")
    private fun saveVideoFile(filePath: String?): File? {
        filePath?.let {
            val videoFile = File(filePath)
            val videoFileName = "${System.currentTimeMillis()}_${videoFile.name}"
            val folderName = Environment.DIRECTORY_MOVIES
            if (Build.VERSION.SDK_INT >= 30) {

                val values = ContentValues().apply {

                    put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            videoFileName
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Images.Media.RELATIVE_PATH, folderName)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val collection =
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val fileUri = context.contentResolver.insert(collection, values)

                fileUri?.let {
                    context.contentResolver.openFileDescriptor(fileUri, "rw")
                            .use { descriptor ->
                                descriptor?.let {
                                    FileOutputStream(descriptor.fileDescriptor).use { out ->
                                        FileInputStream(videoFile).use { inputStream ->
                                            val buf = ByteArray(4096)
                                            while (true) {
                                                val sz = inputStream.read(buf)
                                                if (sz <= 0) break
                                                out.write(buf, 0, sz)
                                            }
                                        }
                                    }
                                }
                            }

                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(fileUri, values, null, null)

                    return File(getMediaPath(context, fileUri))
                }
            } else {
                val downloadsPath =
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val desFile = File(downloadsPath, videoFileName)

                if (desFile.exists())
                    desFile.delete()

                try {
                    desFile.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                return desFile
            }
        }
        return null
    }


    private fun processVideoWithoutCompress(filePath: String): String? {
        val newfile: File
        try {
            val currentFile = File(filePath)
            val loc = Environment.getExternalStorageDirectory()
            val directory = File(loc.absolutePath.toString() + "/Vikram")
            directory.mkdir()
            val fileName = currentFile.name + ".mp4"
            newfile = File(directory, fileName)


            if (currentFile.exists()) {
                val `in`: InputStream = FileInputStream(currentFile)
                val out: OutputStream = FileOutputStream(newfile)

                // Copy the bits from instream to outstream
                val buf = ByteArray(1024)
                var len: Int
                while (`in`.read(buf).also { len = it } > 0) {
                    out.write(buf, 0, len)
                }
                `in`.close()
                out.close()
                Log.e("Saved", "Video file saved successfully.")
            } else {
                Log.e("Saved", "Video saving failed. Source file missing.")
            }

            return newfile.path
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return null
    }


    private fun uploadVideoOnAws(filePath: String) {
        val credentials = BasicAWSCredentials(
                context.getString(R.string.s3_access_key),
                context.getString(R.string.s3_secret)
        )

        val name = filePath.substring(filePath.lastIndexOf("/") + 1)
        val removeSpaceFileName = name.replace(" ", "_")
        val fileName = removeSpaceFileName.replace("-", "_")

        videoUrl = "https://d1ik1ve0yltxwm.cloudfront.net/multitv/video/960/$fileName"

        Log.e("Video Url :::", videoUrl)


        val s3Client = AmazonS3Client(credentials)
        var folderName = "multitv/video/960/$fileName"
        // backet name ott960

        val transferUtility = TransferUtility.builder()
                .context(context)
                .awsConfiguration(AWSConfiguration(context))
                .s3Client(s3Client)
                .build()
        val imageFile = File(filePath)


        if (imageFile.isAbsolute)
            Log.e("Image path ===", "" + filePath)
        else
            Log.e("Image path ===", "" + "Null")

        var uploadObserver: TransferObserver? = null

        uploadObserver = transferUtility.upload(
                "ott960", // The S3 bucket to upload to
                folderName, imageFile, CannedAccessControlList.PublicReadWrite
        )

        //var fileUrl = "https://d3tn0h9cityxqm.cloudfront.net/urbanstar/images/" + removeSpaceFileName

        uploadObserver?.setTransferListener(object : TransferListener {

            override fun onStateChanged(id: Int, state: TransferState) {
                if (TransferState.COMPLETED == state) {
                    Log.e("file uploaded======", "backetname:::::" + uploadObserver.bucket)
                    Log.e("file uploaded======", "path:::::" + uploadObserver.absoluteFilePath)
                    Log.e("file uploaded======", "key:::::" + uploadObserver.key)
                    Log.e("file uploaded======", "bytes:::::" + uploadObserver.bytesTotal)

                    Log.e("file uploaded======", "completed")

                    Toast.makeText(context, "Video Upload Completed!", Toast.LENGTH_SHORT).show()

                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                val percentDonef = bytesCurrent.toFloat() / bytesTotal.toFloat() * 100
                val percentDone = percentDonef.toInt()

                Log.e(
                        "YourActivity", "ID:" + id + " bytesCurrent: " + bytesCurrent
                        + " bytesTotal: " + bytesTotal + " " + percentDone + "%"
                )


                if (percentDone <= 100 && percentDone.toInt() % 5 == 0) {
                    //percentDone.toLong()
                }


            }

            override fun onError(id: Int, ex: Exception) {
                Log.e("video  uploaded======", "error===" + ex.message)
                Toast.makeText(context, "Video Upload Failed!", Toast.LENGTH_SHORT).show()
            }

        })
    }


}