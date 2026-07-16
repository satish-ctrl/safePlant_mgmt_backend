import torch
import torch.nn as nn
import numpy as np

class Encoder(nn.Module):
    def __init__(self, seq_len, no_features, embedding_dim):
        super(Encoder, self).__init__()
        self.seq_len = seq_len
        self.no_features = no_features
        self.embedding_dim = embedding_dim
        self.hidden_dim = 2 * embedding_dim
        
        # 2-layer LSTM Encoder
        self.rnn1 = nn.LSTM(
            input_size=no_features,
            hidden_size=self.hidden_dim,
            num_layers=1,
            batch_first=True
        )
        self.rnn2 = nn.LSTM(
            input_size=self.hidden_dim,
            hidden_size=embedding_dim,
            num_layers=1,
            batch_first=True
        )
        
    def forward(self, x):
        x, _ = self.rnn1(x)
        _, (hidden, _) = self.rnn2(x)
        return hidden.squeeze(0)

class Decoder(nn.Module):
    def __init__(self, seq_len, input_dim, out_features):
        super(Decoder, self).__init__()
        self.seq_len = seq_len
        self.input_dim = input_dim
        self.hidden_dim = 2 * input_dim
        
        self.rnn1 = nn.LSTM(
            input_size=input_dim,
            hidden_size=input_dim,
            num_layers=1,
            batch_first=True
        )
        self.rnn2 = nn.LSTM(
            input_size=input_dim,
            hidden_size=self.hidden_dim,
            num_layers=1,
            batch_first=True
        )
        self.output_layer = nn.Linear(self.hidden_dim, out_features)
        
    def forward(self, x):
        x = x.unsqueeze(1).repeat(1, self.seq_len, 1)
        x, _ = self.rnn1(x)
        x, _ = self.rnn2(x)
        return self.output_layer(x)

class LSTMAutoencoder(nn.Module):
    def __init__(self, seq_len, no_features, embedding_dim):
        super(LSTMAutoencoder, self).__init__()
        self.encoder = Encoder(seq_len, no_features, embedding_dim)
        self.decoder = Decoder(seq_len, embedding_dim, no_features)
        
    def forward(self, x):
        bottleneck = self.encoder(x)
        reconstructed = self.decoder(bottleneck)
        return reconstructed

def calculate_reconstruction_error(model, data_loader, criterion, device="cpu"):
    """Calculates reconstruction errors for all sequences in data_loader."""
    model.eval()
    errors = []
    with torch.no_grad():
        for batch in data_loader:
            x = batch[0].to(device)
            reconstructed = model(x)
            loss = torch.mean((x - reconstructed) ** 2, dim=(1, 2))
            errors.extend(loss.cpu().numpy())
    return np.array(errors)
