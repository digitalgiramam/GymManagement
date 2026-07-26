# Fix "No value passed for parameter 'location'" Build Error

The build is failing because `UpdateMemberRequest` was likely updated with new required parameters (`location` and `joinDate`), but the call in `MemberDetailViewModel.kt` was not updated to provide them.

## Proposed Changes

### [Component Name] Data Model & ViewModel

#### [MODIFY] [Models.kt](file:///C:/Users/Administrator/Claude/Projects/GymManagement/android/app/src/main/java/com/gymmanager/data/model/Models.kt)
- Add default `null` values to all parameters in `UpdateMemberRequest`. This makes the class more flexible for partial updates and prevents similar build errors in the future when new optional fields are added.

#### [MODIFY] [MemberDetailViewModel.kt](file:///C:/Users/Administrator/Claude/Projects/GymManagement/android/app/src/main/java/com/gymmanager/ui/members/MemberDetailViewModel.kt)
- Update the `UpdateMemberRequest` instantiation in `toggleStatus` to only pass the `status` parameter, relying on the new default values for other fields.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the fix.
