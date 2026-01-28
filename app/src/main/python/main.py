from enum import Enum
import json
import traceback
from datetime import datetime, timezone
from io import BytesIO
import base64
import NSKeyedUnArchiver

from findmy import FindMyAccessory
from findmy.reports import (
    RemoteAnisetteProvider,
    AppleAccount,
    LoginState,
    SmsSecondFactorMethod,
    TrustedDeviceSecondFactorMethod
)
from findmy.reports.twofactor import (
    SyncSecondFactorMethod
)


class TwoFactorMethods(Enum):
    UNKNOWN = 0
    TRUSTED_DEVICE = 1
    PHONE = 2


def _toUnixEpochMs(dt: datetime) -> int:
    """
    Convert datetime to unix epoch (milliseconds)
    """
    if dt is None:
        return None
    return int(dt.timestamp() * 1000)


def foo(arg: str):
    """For testing..."""
    print("Bar!")
    print(f"The arg was: '{arg}'")

    for i in range(10):
        print(f"Hello {i}!")

    print("Done!")

    return {
        "Some": "Dictionary",
        "key": [1, 2, 3],
        "other": False,
        "and": True,
        "nested": {
            "a": "b",
            "c": "d"
        },
        "set": {"a", "b", "c"},
        "floats": 1.123456,
        "null maybe": None
    }


def decodeBeaconNamingRecordCloudKitMetadata(cleanedBase64: str) -> dict:
    """
    Extract some extra information from within the plist file `cloudKitMetadata` node
    (that is followed by a `<data>` element containing base64)

    Note that `cleanedBase64` must not contain line breaks `\\n`, or tabs `\\t`
    or other whitespace characters that get introduced by some plist parsers


    ### More info:

    The most popular java plist parser, the google java [dd-plist](https://mvnrepository.com/artifact/com.googlecode.plist/dd-plist) library,
    [does not currently support the `NSKeyedArchiver` plist format](https://github.com/3breadt/dd-plist/issues/70) (at the time of writing).

    However somebody has managed to create a parser in python: https://github.com/avibrazil/NSKeyedUnArchiver

    So because there's some interesting data to be extracted from within this plist,
    python is used.
    """
    try:
        decoded = base64.b64decode(cleanedBase64)
        unarchiver = NSKeyedUnArchiver.NSKeyedUnArchiver(decoded)
        output = unarchiver.decode()
        # output is usually a dict-like structure
        return output
    except Exception:
        print(f"Failed to decode cloudKitMetadata due to error: {traceback.format_exc()}")
        return None


def _convertToJavaDictWrapper(method) -> dict:
    """
    Convert a second-factor method class instance to a dict which java can understand.
    """
    return_obj = {
        "type": TwoFactorMethods.UNKNOWN.value,
        "method": method
    }

    if isinstance(method, TrustedDeviceSecondFactorMethod):
        return_obj["type"] = TwoFactorMethods.TRUSTED_DEVICE.value

    if isinstance(method, SmsSecondFactorMethod):
        return_obj["type"] = TwoFactorMethods.PHONE.value

    if isinstance(method, SyncSecondFactorMethod):
        return_obj["type"] = TwoFactorMethods.TRUSTED_DEVICE.value

    return return_obj


def loginSync(email: str, password: str, anisetteServerUrl: str) -> dict:
    try:
        anisette = RemoteAnisetteProvider(anisetteServerUrl)
        acc = AppleAccount(anisette)

        state = acc.login(email, password)

        if state == LoginState.REQUIRE_2FA:  # Account requires 2FA
            methods = acc.get_2fa_methods()

            named_methods_list = []  # create a map for use in Java...
            for method in methods:
                named_methods_list.append(
                    _convertToJavaDictWrapper(method)
                )

            # Java needs to show us a nice UI
            # where we can select how we want to auth...
            return {
                "account": acc,
                "loginState": state.value,
                "loginMethods": named_methods_list
            }

        # Any of the other cases. I'm not sure if this can even happen...
        return {
            "account": acc,
            "loginState": state.value,
            "loginMethods": None
        }

    except Exception as e:
        print(f"Failed to log in due to error: {traceback.format_exc()}")
        return {
            "error": str(e)
        }


def exportToString(account: AppleAccount) -> str:
    return json.dumps(account.export())


def getAccount(
        serializedAccountData: str, anisetteServerUrl: str) -> AppleAccount:
    try:
        data = json.loads(serializedAccountData)

        anisette = RemoteAnisetteProvider(anisetteServerUrl)
        acc = AppleAccount(anisette)
        acc.restore(data)

        print(f"Login State: {acc.login_state}")

        return acc
    except Exception:
        err = traceback.format_exc()
        print(f"Failed to restore account from string: {err}")
        return None


def getLastReports(
        account: AppleAccount,
        idToPList,
        hoursBack: int) -> dict:
    # JAVA typing: see https://chaquo.com/chaquopy/doc/current/python.html
    # especially this: https://chaquo.com/chaquopy/doc/current/python.html#classes
    #
    # IMPORTANT:
    # - Never return None here: Chaquopy maps Python None -> Java null, which makes the Java layer
    #   throw a generic exception and hides the real cause.
    # - If one beacon fails, continue with the others and return partial results.
    res = {}

    try:
        num_items = idToPList.size()
        print(f"num_items is {num_items}")

        for i in range(0, num_items):
            pair = idToPList.get(i)
            beaconId = pair.first
            plistContent = pair.second

            try:
                print(f"Fetching report for {beaconId} for the last {hoursBack} hours...")
                fp = BytesIO(plistContent.encode('utf-8'))
                airtag = FindMyAccessory.from_plist(fp)

                reports = account.fetch_last_reports(airtag, hoursBack)
                print(f"Got {len(reports)} reports for {beaconId}")

                items = []
                for report in sorted(reports):
                    items.append({
                        "publishedAt": _toUnixEpochMs(report.published_at),
                        "description": report.description,
                        "timestamp": _toUnixEpochMs(report.timestamp),
                        "confidence": report.confidence,
                        "latitude": report.latitude,
                        "longitude": report.longitude,
                        "horizontalAccuracy": report.horizontal_accuracy,
                        "status": report.status
                    })

                res[beaconId] = items

            except Exception:
                # Keep going for the remaining beacons, but log the full traceback for this one.
                err = traceback.format_exc()
                print(f"Failed to fetch reports for beaconId={beaconId}: {err}")
                res[beaconId] = []

        return res

    except Exception:
        # Something unexpected happened at the function level.
        # Return what we have so far (or empty), but log the reason.
        err = traceback.format_exc()
        print(f"Failed to fetch all reports due to error: {err}")
        return res


def getReports(
        account: AppleAccount,
        idToPList,
        unixStartMs: int,
        unixEndMs: int) -> dict:
    # JAVA typing: see https://chaquo.com/chaquopy/doc/current/python.html
    # especially this: https://chaquo.com/chaquopy/doc/current/python.html#classes
    #
    # IMPORTANT: Never return None (would become Java null). Return partial results on error.
    res = {}

    try:
        num_items = idToPList.size()
        print(f"num_items is {num_items}")

        for i in range(0, num_items):
            pair = idToPList.get(i)
            beaconId = pair.first
            plistContent = pair.second

            try:
                print(f"Fetching report for {beaconId} in time range {unixStartMs}-{unixEndMs}...")
                fp = BytesIO(plistContent.encode('utf-8'))
                airtag = FindMyAccessory.from_plist(fp)

                start: datetime = datetime.fromtimestamp(
                    unixStartMs/1000,
                    tz=timezone.utc
                )
                end: datetime = datetime.fromtimestamp(
                    unixEndMs/1000,
                    tz=timezone.utc
                )

                reports = account.fetch_reports(airtag, start, end)
                print(f"Got {len(reports)} reports for {beaconId} for time range {unixStartMs}-{unixEndMs}")

                items = []
                for report in sorted(reports):
                    items.append({
                        "publishedAt": _toUnixEpochMs(report.published_at),
                        "description": report.description,
                        "timestamp": _toUnixEpochMs(report.timestamp),
                        "confidence": report.confidence,
                        "latitude": report.latitude,
                        "longitude": report.longitude,
                        "horizontalAccuracy": report.horizontal_accuracy,
                        "status": report.status
                    })

                res[beaconId] = items

            except Exception:
                err = traceback.format_exc()
                print(f"Failed to fetch reports for beaconId={beaconId}: {err}")
                res[beaconId] = []

        return res

    except Exception:
        err = traceback.format_exc()
        print(f"Failed to fetch all reports due to error: {err}")
        return res
