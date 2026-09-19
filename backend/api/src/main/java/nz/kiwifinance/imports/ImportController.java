package nz.kiwifinance.imports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
class ImportController {

    private final ImportService importService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ImportResponse importCsv(CurrentUser user, @RequestParam UUID accountId, @RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(ErrorCode.IMPORT_FORMAT_UNRECOGNISED, "The file is empty.");
        }
        String name = file.getOriginalFilename() == null ? "import.csv" : file.getOriginalFilename();
        try {
            return importService.importCsv(
                    user.id(), accountId, truncate(name), new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException(ErrorCode.IMPORT_FORMAT_UNRECOGNISED, "We couldn't read that file.");
        }
    }

    @GetMapping
    List<ImportResponse> recent(CurrentUser user) {
        return importService.recent(user.id());
    }

    private static String truncate(String name) {
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }
}
