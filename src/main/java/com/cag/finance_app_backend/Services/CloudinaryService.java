package com.cag.finance_app_backend.Services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloud) {
        this.cloudinary = cloud;
    }

    @SuppressWarnings("unchecked")
    public String uploadFile(MultipartFile file) throws IOException {
        // Upload the file to Cloudinary
        Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
        
        // Return the secure URL provided by Cloudinary
        return uploadResult.get("secure_url").toString();
    }
}