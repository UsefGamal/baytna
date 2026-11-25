package com.baytna.app.data.repo

import com.baytna.domain.finance.ValidationError

class ValidationException(val errors: List<ValidationError>) : IllegalArgumentException("Validation failed: $errors")

/** The row changed since it was loaded. Financial edits never silently overwrite. */
class VersionConflictException(val entity: String, val id: String) : IllegalStateException("$entity $id was changed elsewhere")
