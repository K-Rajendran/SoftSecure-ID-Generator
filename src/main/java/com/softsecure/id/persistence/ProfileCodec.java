package com.softsecure.id.persistence;

import com.softsecure.id.domain.Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.LocalDate;

/** Explicit, bounded format; never Java object deserialization. */
public final class ProfileCodec {
    private ProfileCodec() { }

    public static byte[] encode(Profile profile) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(1);
            for (String field : new String[] {profile.identifier(), profile.name(), profile.department(),
                    profile.gender(), profile.bloodGroup(), profile.address(), profile.phone(), profile.alternatePhone(),
                    profile.email(), profile.birthDate().toString(), profile.issueDate().toString()}) {
                output.writeUTF(field);
            }
            byte[] photo = profile.photo();
            output.writeInt(photo.length);
            output.write(photo);
        }
        byte[] result = buffer.toByteArray();
        if (result.length > 2_100_000) {
            java.util.Arrays.fill(result, (byte) 0);
            throw new IOException("Profile exceeds size limit");
        }
        return result;
    }

    public static Profile decode(byte[] bytes) throws IOException {
        if (bytes.length > 2_100_000) {
            throw new IOException("Profile exceeds size limit");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 1) {
                throw new IOException("Unsupported profile version");
            }
            String[] fields = new String[11];
            for (int i = 0; i < fields.length; i++) {
                fields[i] = input.readUTF();
            }
            int length = input.readInt();
            if (length < 0 || length > 2_000_000) {
                throw new IOException("Invalid portrait size");
            }
            byte[] photo = input.readNBytes(length);
            if (photo.length != length || input.available() != 0) {
                throw new IOException("Invalid profile payload");
            }
            return new Profile(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6],
                    fields[7], fields[8], LocalDate.parse(fields[9]), LocalDate.parse(fields[10]), photo);
        }
    }
}
