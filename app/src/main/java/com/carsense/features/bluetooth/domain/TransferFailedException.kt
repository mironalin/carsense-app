package com.carsense.features.bluetooth.domain

import java.io.IOException

class TransferFailedException : IOException("Reading incoming data failed")